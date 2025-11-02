package com.nklmthr.finance.personal.scheduler;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.openai.OpenAIClient;
import com.nklmthr.finance.personal.repository.AppUserRepository;
import com.nklmthr.finance.personal.scheduler.config.ExtractionConfig;
import com.nklmthr.finance.personal.scheduler.config.ExtractionConfigRegistry;
import com.nklmthr.finance.personal.scheduler.util.PatternResult;
import com.nklmthr.finance.personal.scheduler.util.TransactionPatternLibrary;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.service.CategoryService;
import com.nklmthr.finance.personal.service.gmail.AppUserDataStoreFactory;
import com.nklmthr.finance.personal.service.gmail.GmailServiceProvider;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher.MatchResult;

/**
 * Unified data extraction service that handles all bank/account type combinations
 * using configuration-based approach instead of separate service classes.
 * 
 * To add a new bank or account type, simply add a new ExtractionConfig to 
 * ExtractionConfigRegistry instead of creating a new service class.
 */
@Service
public class ConfigurableDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(ConfigurableDataExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Autowired
	private ExtractionConfigRegistry configRegistry;

	@Autowired
	private AccountFuzzyMatcher accountFuzzyMatcher;

	@Autowired
	private AccountService accountService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AccountTransactionService accountTransactionService;

	@Autowired
	private GmailServiceProvider gmailServiceProvider;

	@Autowired
	private AppUserRepository appUserRepository;

	@Value("${gmail.lookback.days:7}")
	private int gmailLookbackDays;

	@Value("${openai.enabled:true}")
	private boolean openAIEnabled;

	@Autowired
	private OpenAIClient openAIClient;

	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping ConfigurableDataExtractionService");
			return;
		}
		run();
	}

	public void run() {
		try {
			String requestId = UUID.randomUUID().toString();
			MDC.put("requestId", requestId);
			logger.info("Start: ConfigurableDataExtractionService");

			// Process each configuration
			for (ExtractionConfig config : configRegistry.getAllConfigs()) {
				logger.info("Processing configuration: {}", config.getName());
				processConfiguration(config);
			}

		} catch (Exception e) {
			logger.error("Error during ConfigurableDataExtractionService execution", e);
		} finally {
			logger.info("Finish: ConfigurableDataExtractionService\n\n");
			MDC.remove("requestId");
		}
	}

	/**
	 * Process only specific configurations by name
	 */
	public void runForSpecificConfigs(List<String> configNames) {
		try {
			String requestId = UUID.randomUUID().toString();
			MDC.put("requestId", requestId);
			logger.info("Start: ConfigurableDataExtractionService (specific configs: {})", configNames);

			// Process only the requested configurations
			for (ExtractionConfig config : configRegistry.getAllConfigs()) {
				if (configNames.contains(config.getName())) {
					logger.info("Processing configuration: {}", config.getName());
					processConfiguration(config);
				}
			}

		} catch (Exception e) {
			logger.error("Error during ConfigurableDataExtractionService execution", e);
		} finally {
			logger.info("Finish: ConfigurableDataExtractionService\n\n");
			MDC.remove("requestId");
		}
	}

	private void processConfiguration(ExtractionConfig config) throws Exception {
		for (AppUser appUser : appUserRepository.findAll()) {
			logger.info("Processing {} for user: {}", config.getName(), appUser.getUsername());

			var clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH))
			);

			AppUserDataStoreFactory factory = 
				new AppUserDataStoreFactory(appUserRepository);

			GoogleAuthorizationCodeFlow flow = 
				new GoogleAuthorizationCodeFlow.Builder(
					GoogleNetHttpTransport.newTrustedTransport(),
					JSON_FACTORY,
					clientSecrets,
					List.of(GmailScopes.GMAIL_READONLY)
				)
				.setDataStoreFactory(factory)
				.setAccessType("offline")
				.build();

			Credential credential = flow.loadCredential(appUser.getUsername());
			if (credential == null || 
			    StringUtils.isBlank(credential.getAccessToken()) ||
			    StringUtils.isAllBlank(credential.getRefreshToken())) {
				logger.warn("Gmail not connected for user: {}", appUser.getUsername());
				continue;
			}

			Gmail gmailService = gmailServiceProvider.getGmailService(appUser);

			List<String> gmailAPIQueries = buildGmailQueries(config);

			for (String query : gmailAPIQueries) {
				List<Message> messages = 
					gmailService.users().messages().list("me").setQ(query).execute().getMessages();

				if (messages == null || messages.isEmpty()) {
					logger.info("No messages found for query: {}", query);
					continue;
				}

				logger.info("{}: {} messages found.", query, messages.size());

				for (Message message : messages) {
					processMessage(gmailService, message, config, appUser);
				}
			}
		}
	}

	private void processMessage(
		Gmail gmailService,
		Message message,
		ExtractionConfig config,
		AppUser appUser
	) throws Exception {
		Message mess = 
			gmailService.users().messages().get("me", message.getId()).setFormat("full").execute();

		String emailContent = extractPlainText(mess);
		logger.debug("Extracted content for message ID {}: {}", mess.getId(), emailContent);

		if (StringUtils.isBlank(emailContent) || 
		    "[Empty content]".equals(emailContent) ||
		    "[Error extracting message]".equals(emailContent)) {
			logger.warn("Skipping message with empty content.");
			return;
		}

		// Handle content-specific filtering (e.g., Axis Savings filters)
		if (shouldSkipBasedOnContent(emailContent, config)) {
			logger.debug("Skipping message based on content filters for {}", config.getName());
			return;
		}

		AccountTransaction accountTransaction = new AccountTransaction();
		accountTransaction.setAppUser(appUser);
		accountTransaction.setCategory(categoryService.getNonClassifiedCategory(appUser));
		accountTransaction.setSourceId(mess.getId());
		accountTransaction.setSourceThreadId(mess.getThreadId());
		accountTransaction.setSourceTime(
			Instant.ofEpochMilli(mess.getInternalDate())
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime()
		);
		accountTransaction.setDate(accountTransaction.getSourceTime());
		accountTransaction.setRawData(emailContent);

		logger.info("Processing email ID: {} with config: {}", mess.getId(), config.getName());
		
		// Extract basic transaction data first (amount, description, date, type) - no account matching
		accountTransaction = extractBasicTransactionData(accountTransaction, emailContent, config);

		if (accountTransaction == null) {
			logger.warn("Failed to extract basic transaction data from email ID: {}. Email content: {}", 
				mess.getId(), emailContent);
			return;
		}

		// Set default currency if not set during extraction
		if (accountTransaction.getCurrency() == null) {
			accountTransaction.setCurrency("INR");
			logger.debug("Set default currency to INR for transaction from email ID: {}", mess.getId());
		}

		// Check for duplicate BEFORE doing expensive account matching
		var duplicateOpt = accountTransactionService.findDuplicate(accountTransaction, appUser);
		if (duplicateOpt.isPresent()) {
			logger.info("Skipping duplicate transaction: {}", accountTransaction.getDescription());
			AccountTransaction existing = duplicateOpt.get();
			if (StringUtils.isBlank(existing.getRawData())) {
				logger.info("Updating missing rawData for duplicate transaction ID: {}", existing.getId());
				existing.setRawData(emailContent);
			}
			accountTransactionService.mergeSourceInfoIfNeeded(existing, accountTransaction);
			return; // Early return - skip account matching
		}

		// Only if not duplicate, do account matching (expensive operation)
		accountTransaction = findAndSetAccount(accountTransaction, emailContent, config, appUser);
		if (accountTransaction == null) {
			logger.warn("Failed to find account for transaction from email ID: {}", mess.getId());
			return;
		}

		// Call OpenAI for enrichment if enabled
		if (openAIEnabled) {
			logger.debug("OpenAI enabled, calling for transaction enrichment");
			openAIClient.getGptResponse(emailContent, accountTransaction);
		} else {
			logger.info("OpenAI disabled, skipping GPT enrichment");
		}

		// Ensure description is never null
		if (accountTransaction.getDescription() == null) {
			logger.warn("Regex extraction failed for description in email ID: {}. Email content: {}", 
				mess.getId(), emailContent);
			accountTransaction.setDescription("Unknown");
		}

		logger.info("Saving transaction: {}", accountTransaction);
		accountTransactionService.save(accountTransaction, appUser);
	}

	private boolean shouldSkipBasedOnContent(String emailContent, ExtractionConfig config) {
		String lowerContent = emailContent.toLowerCase();

		// Always skip declined transactions
		if (lowerContent.contains("has been declined") ||
		    lowerContent.contains("incorrect pin") ||
		    lowerContent.contains("transaction declined") ||
		    lowerContent.contains("transaction failed")) {
			logger.info("Skipping declined/failed transaction for {}", config.getName());
			return true;
		}

		return false;
	}

	/**
	 * Extract basic transaction data (amount, description, date, type) without account matching.
	 * This is done first to enable early duplicate checking before expensive account matching.
	 */
	private AccountTransaction extractBasicTransactionData(
		AccountTransaction tx,
		String emailContent,
		ExtractionConfig config
	) {
		try {
			// Use pattern library for extraction
			PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
			if (amountResult.isPresent()) {
				tx.setAmount(amountResult.getValue());
				logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
			}

			PatternResult<String> descriptionResult = TransactionPatternLibrary.extractDescription(emailContent);
			if (descriptionResult.isPresent()) {
				tx.setDescription(descriptionResult.getValue());
				logger.debug("Extracted description using pattern: {}", descriptionResult.getMatchedPattern());
			}

			// Set transaction type based on configuration
			if (config.hasFixedTransactionType()) {
				tx.setType(config.getFixedTransactionType());
				logger.debug("Using fixed transaction type: {}", config.getFixedTransactionType());
			} else {
				tx.setType(TransactionPatternLibrary.detectTransactionType(emailContent));
				logger.debug("Auto-detected transaction type: {}", tx.getType());
			}

			return tx;
		} catch (Exception e) {
			logger.error("Error parsing {} transaction", config.getName(), e);
			return null;
		}
	}

	/**
	 * Find and set the account for a transaction using fuzzy matching.
	 * This is done AFTER duplicate checking to avoid expensive operations for duplicates.
	 */
	private AccountTransaction findAndSetAccount(
		AccountTransaction tx,
		String emailContent,
		ExtractionConfig config,
		AppUser appUser
	) {
		try {
			// Use fuzzy matching to find the best account
			List<AccountDTO> accounts = accountService.getAllAccounts(appUser);
			MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
				accounts,
				emailContent,
				tx.getDescription()
			);

			if (!matchResult.isValid()) {
				logger.error("Failed to fuzzy match account for {} transaction. Email content: {}", 
					config.getName(), emailContent);
				return null;
			}

			Account matchedAccount = accountService.getAccountByName(
				matchResult.account().name(),
				appUser
			);
			tx.setAccount(matchedAccount);
			logger.info("Fuzzy matched account: {} with score {}", matchResult.account().name(), matchResult.score());

			return tx;
		} catch (Exception e) {
			logger.error("Error finding account for {} transaction", config.getName(), e);
			return null;
		}
	}

	private List<String> buildGmailQueries(ExtractionConfig config) {
		List<String> queries = new ArrayList<>();
		LocalDate today = LocalDate.now();
		LocalDate lookbackDate = today.minusDays(gmailLookbackDays);

		for (String subject : config.getEmailSubjects()) {
			String gmailQuery = String.format("subject:(%s) from:(%s) after:%s before:%s",
				subject,
				config.getSender(),
				formatDate(lookbackDate),
				formatDate(today.plusDays(1))
			);
			queries.add(gmailQuery);
			logger.debug("Gmail API query: {}", gmailQuery);
		}
		return queries;
	}

	private String formatDate(LocalDate date) {
		return String.format("%d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
	}

	// Email text extraction methods (same as AbstractDataExtractionService)
	private String extractPlainText(Message message) {
		try {
			if (message == null) {
				logger.warn("Null message received for extraction");
				return "[Null message]";
			}

			MessagePart payload = message.getPayload();
			if (payload == null) {
				logger.warn("No payload in message ID: {}", message.getId());
				return "[No payload]";
			}

			logger.debug("Message ID: {}, MIME type: {}", message.getId(), payload.getMimeType());

			String text = extractTextFromMessagePart(payload);

			if (StringUtils.isBlank(text)) {
				logger.warn("Extracted blank text from message ID: {}, MIME: {}, has parts: {}",
					message.getId(),
					payload.getMimeType(),
					payload.getParts() != null && !payload.getParts().isEmpty());
				return "[Empty content]";
			}

			String cleaned = cleanText(text.strip());
			logger.debug("Successfully extracted {} characters from message ID: {}",
				cleaned.length(), message.getId());

			return cleaned;
		} catch (Exception e) {
			logger.error("Failed to extract plain text from message: {}", e.getMessage(), e);
			return "[Error extracting message]";
		}
	}

	private String cleanText(String input) {
		if (input == null)
			return "";

		String cleaned = input.replaceAll("[\\u00A0\\u2007\\u202F\\u200B\\uFEFF]", " ")
			.replaceAll("\\s+", " ")
			.trim();

		return cleaned;
	}

	private String extractTextFromMessagePart(MessagePart part) {
		if (part == null)
			return null;

		String mimeType = part.getMimeType();
		if ("text/plain".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
			return decodeBase64(part.getBody().getData());
		}

		if ("text/html".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
			String html = decodeBase64(part.getBody().getData());
			return Jsoup.parse(html).text();
		}

		if (part.getParts() != null && !part.getParts().isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (MessagePart subPart : part.getParts()) {
				String subResult = extractTextFromMessagePart(subPart);
				if (StringUtils.isNotBlank(subResult)) {
					if (sb.length() > 0)
						sb.append("\n");
					sb.append(subResult);
				}
			}
			return sb.toString();
		}

		return null;
	}

	private String decodeBase64(String data) {
		if (StringUtils.isBlank(data)) {
			logger.debug("Empty data provided for base64 decoding");
			return null;
		}

		try {
			byte[] bytes = Base64.getUrlDecoder().decode(data);
			String decoded = new String(bytes, StandardCharsets.UTF_8);
			logger.debug("Successfully decoded {} bytes using URL decoder", bytes.length);
			return decoded;
		} catch (IllegalArgumentException e) {
			logger.warn("Failed to decode base64 with URL decoder: {}, trying standard decoder", e.getMessage());
			try {
				byte[] bytes = Base64.getDecoder().decode(data);
				String decoded = new String(bytes, StandardCharsets.UTF_8);
				logger.debug("Successfully decoded {} bytes using standard decoder", bytes.length);
				return decoded;
			} catch (Exception e2) {
				logger.error("Both URL and standard base64 decoders failed", e2);
				return null;
			}
		}
	}
}

