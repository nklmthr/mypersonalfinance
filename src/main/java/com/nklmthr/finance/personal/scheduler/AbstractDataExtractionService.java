package com.nklmthr.finance.personal.scheduler;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.openai.OpenAIClient;
import com.nklmthr.finance.personal.repository.AppUserRepository;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.service.CategoryService;
import com.nklmthr.finance.personal.service.gmail.AppUserDataStoreFactory;
import com.nklmthr.finance.personal.service.gmail.GmailServiceProvider;

public abstract class AbstractDataExtractionService {
	private static final Logger logger = LoggerFactory.getLogger(AbstractDataExtractionService.class);

	@Autowired
	protected AccountService accountService;
	@Autowired
	protected CategoryService categoryService;
	@Autowired
	protected AccountTransactionService accountTransactionService;

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

	public void run() {
		try {
			String requestId = UUID.randomUUID().toString();
	        MDC.put("requestId", requestId);
			logger.info("Start: {}", this.getClass().getSimpleName());
			for (AppUser appUser : appUserRepository.findAll()) {
				logger.info("Processing for user: {}", appUser.getUsername());
				var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
						new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));
				AppUserDataStoreFactory factory = new AppUserDataStoreFactory(appUserRepository);
				GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
						GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets,
						List.of(GmailScopes.GMAIL_READONLY)).setDataStoreFactory(factory).setAccessType("offline")
						.build();

				Credential credential = flow.loadCredential(appUser.getUsername());
				if (credential == null || StringUtils.isBlank(credential.getAccessToken())
						|| StringUtils.isAllBlank(credential.getRefreshToken())) {
					logger.warn("Gmail not connected for user: " + appUser.getUsername());
					continue;
				}
				Gmail gmailService = gmailServiceProvider.getGmailService(appUser);

				List<String> gmailAPIQueries = getGMailAPIQuery();

				if (gmailAPIQueries == null || gmailAPIQueries.isEmpty()) {
					logger.info("No queries found for {}", this.getClass().getSimpleName());
					continue;
				}

				for (String query : gmailAPIQueries) {
					List<Message> messages = gmailService.users().messages().list("me").setQ(query).execute()
							.getMessages();

					if (messages == null || messages.isEmpty()) {
						logger.info("No messages found for query: {}", query);
						continue;
					}

					logger.info("{}: {} messages found.", query, messages.size());

				for (Message message : messages) {
					Message mess = gmailService.users().messages().get("me", message.getId()).setFormat("full")
							.execute();

					String emailContent = extractPlainText(mess);
					logger.debug("Extracted content for message ID {}: {}", mess.getId(), emailContent);
					
					if (StringUtils.isBlank(emailContent) || "[Empty content]".equals(emailContent)
							|| "[Error extracting message]".equals(emailContent)) {
						logger.warn("Skipping message with empty content.");
						continue;
					}

					AccountTransaction accountTransaction = new AccountTransaction();
					accountTransaction.setAppUser(appUser);
					accountTransaction.setCategory(categoryService.getNonClassifiedCategory(appUser));
					accountTransaction.setSourceId(mess.getId());
					accountTransaction.setSourceThreadId(mess.getThreadId());
					accountTransaction.setSourceTime(Instant.ofEpochMilli(mess.getInternalDate())
							.atZone(ZoneId.systemDefault()).toLocalDateTime());
					
					accountTransaction.setDate(accountTransaction.getSourceTime());
					
					// Set raw data immediately to ensure it's captured for all transactions
					accountTransaction.setRawData(emailContent);
					logger.info("Extracting from email content: {}", emailContent);
					accountTransaction = extractTransactionData(accountTransaction, emailContent, appUser);
					
					// Check if extraction was successful
					if (accountTransaction == null) {
						logger.warn("Failed to extract transaction data from email content, skipping message ID: {}", mess.getId());
						continue;
					}
					
					var duplicateOpt = accountTransactionService.findDuplicate(accountTransaction, appUser);
					if (duplicateOpt.isPresent()) {
						logger.info("Skipping duplicate transaction: {}", accountTransaction.getDescription());
						// Update rawData for duplicate if it's missing
						AccountTransaction existing = duplicateOpt.get();
						if (StringUtils.isBlank(existing.getRawData())) {
							logger.info("Updating missing rawData for duplicate transaction ID: {}", existing.getId());
							existing.setRawData(emailContent);
						}
						accountTransactionService.mergeSourceInfoIfNeeded(existing, accountTransaction);
					} else {
						// Call OpenAI for enrichment if enabled
						if (openAIEnabled) {
							logger.debug("OpenAI enabled, calling for transaction enrichment");
							openAIClient.getGptResponse(emailContent, accountTransaction);
						} else {
							logger.info("OpenAI disabled (profile: {}), skipping GPT enrichment", 
								System.getProperty("spring.profiles.active", "default"));
						}
						
						// Ensure description is never null (database constraint)
						// Do NOT copy from gptDescription - keep fields completely separate
						if (accountTransaction.getDescription() == null) {
							logger.warn("Regex extraction failed for description, setting default value");
							accountTransaction.setDescription("Unknown");
						}
						
						logger.info("Saving transaction: {}", accountTransaction);
						accountTransactionService.save(accountTransaction, appUser);
					}
				}
				}
			}
		} catch (Exception e) {
			logger.error("Error during {} execution", this.getClass().getSimpleName(), e);
		} finally {
			logger.info("Finish: {} in ms\n\n", this.getClass().getSimpleName());
			MDC.remove("requestId");
		}
	}

	protected String extractPlainText(Message message) {
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

	public String cleanText(String input) {
		if (input == null)
			return "";

		// Remove non-breaking spaces, zero-width spaces, etc.
		String cleaned = input.replaceAll("[\\u00A0\\u2007\\u202F\\u200B\\uFEFF]", " ") // Non-breaking and zero-width
																						// spaces
				.replaceAll("\\s+", " ") // Normalize all whitespace
				.trim();

		return cleaned;
	}

	private String extractTextFromMessagePart(MessagePart part) {
	    if (part == null) return null;

	    String mimeType = part.getMimeType();
	    if ("text/plain".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
	        return decodeBase64(part.getBody().getData());
	    }

	    if ("text/html".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
	        String html = decodeBase64(part.getBody().getData());
	        return Jsoup.parse(html).text(); // strip HTML tags
	    }

	    if (part.getParts() != null && !part.getParts().isEmpty()) {
	        StringBuilder sb = new StringBuilder();
	        for (MessagePart subPart : part.getParts()) {
	            String subResult = extractTextFromMessagePart(subPart);
	            if (StringUtils.isNotBlank(subResult)) {
	                if (sb.length() > 0) sb.append("\n");
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
			// Try standard decoder as fallback
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

	protected List<String> getGMailAPIQuery() {
		List<String> queries = new java.util.ArrayList<>();
		LocalDate today = LocalDate.now();
		LocalDate lookbackDate = today.minusDays(gmailLookbackDays);
		for (String query : getEmailSubject()) {
			String gmailQuery = String.format("subject:(%s) from:(%s) after:%s before:%s", 
					query, 
					getSender(),
					formatDate(lookbackDate), 
					formatDate(today.plusDays(1)));
			queries.add(gmailQuery);
			logger.debug("Gmail API query: {}", gmailQuery);
		}
		return queries;
	}

	private String formatDate(LocalDate date) {
		return String.format("%d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
	}

	protected abstract List<String> getEmailSubject();

	protected abstract String getSender();

	protected abstract AccountTransaction extractTransactionData(AccountTransaction accountTransaction,
			String emailContent, AppUser appUser);

}
