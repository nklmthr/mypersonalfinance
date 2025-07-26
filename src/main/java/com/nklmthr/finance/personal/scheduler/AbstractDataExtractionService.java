package com.nklmthr.finance.personal.scheduler;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

	public void run() {
		logger.info("\n\nStart: {}\n", this.getClass().getSimpleName());
		try {
			for (AppUser appUser : appUserRepository.findAll()) {
				logger.info("Processing for user: {}", appUser.getUsername());
				var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
						new InputStreamReader(getClass().getResourceAsStream(CREDENTIALS_FILE_PATH)));
				AppUserDataStoreFactory factory = new AppUserDataStoreFactory(appUser, appUserRepository);
				GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
						GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets,
						List.of(GmailScopes.GMAIL_READONLY)).setDataStoreFactory(factory).setAccessType("offline")
						.build();

				Credential credential = flow.loadCredential("user");
				if (credential == null || StringUtils.isBlank(credential.getAccessToken())
						|| StringUtils.isAllBlank(credential.getRefreshToken())) {
					logger.warn("Gmail not connected for user: " + appUser.getUsername());
					return; // Skip this user if token missing
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

						extractTransactionData(accountTransaction, emailContent, appUser);

						if (accountTransactionService.isTransactionAlreadyPresent(accountTransaction, appUser)) {
							logger.info("Skipping duplicate transaction: {}", accountTransaction.getDescription());
						} else {
							accountTransactionService.save(accountTransaction, appUser);
							logger.debug("Saved transaction: {}", accountTransaction.getDescription());
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error during {} execution", this.getClass().getSimpleName(), e);
		} finally {
			logger.info("Finish: {} in ms", this.getClass().getSimpleName());
		}
	}

	protected String extractPlainText(Message message) {
		try {
			if (message == null)
				return "[Null message]";

			MessagePart payload = message.getPayload();
			if (payload == null)
				return "[No payload]";

			String text = extractTextFromMessagePart(payload);
			return StringUtils.isNotBlank(text) ? cleanText(text.strip()) : "[Empty content]";
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
		if (part == null)
			return null;

		String mimeType = part.getMimeType();
		if ("text/plain".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
			return decodeBase64(part.getBody().getData());
		}

		if ("text/html".equalsIgnoreCase(mimeType) && part.getBody() != null && part.getBody().getData() != null) {
			String html = decodeBase64(part.getBody().getData());
			return Jsoup.parse(html).text(); // strip HTML tags
		}

		// Recursively search subparts
		if (part.getParts() != null) {
			for (MessagePart subPart : part.getParts()) {
				String subResult = extractTextFromMessagePart(subPart);
				if (StringUtils.isNotBlank(subResult))
					return subResult;
			}
		}

		return null;
	}

	private String decodeBase64(String data) {
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(data);
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			logger.warn("Failed to decode base64: {}", e.getMessage());
			return null;
		}
	}

	protected List<String> getGMailAPIQuery() {
		List<String> queries = new java.util.ArrayList<>();
		LocalDate today = LocalDate.now();
		LocalDate twoMonthAgo = today.minusDays(15);
		for (String query : getEmailSubject()) {
			queries.add(String.format("subject:(%s) from:(%s) after:%s before:%s", query, getSender(),
					formatDate(twoMonthAgo), formatDate(today.plusDays(1))));
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
