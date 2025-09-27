package com.nklmthr.finance.personal.openai;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.FuzzyScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.service.AccountService;

@Service
public class OpenAIClient {
	@Value("${openai.gpt-os-20b.host}")
	private String host;
	private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
	private static final FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);

	@Autowired
	private AccountService accountService;

	public static void main(String[] args) {
		OpenAIClient client = new OpenAIClient();
		String response = client.callOpenAI("Hello From Nikhil");
		logger.info("Response: {}", response);
	}

	private String callOpenAI(String prompt) {
		RestTemplate restTemplate = new RestTemplate();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer YOUR_API_KEY");
		String systemPrompt = """
				You are a financial data extraction expert.
				Extract structured transaction details from raw bank/credit card email text.

				⚠️ Important:
				- Always respond with valid JSON ONLY (no Markdown, no text outside JSON).
				- The JSON must exactly match this schema:

				{
				  "id": null,
				  "date": "YYYY-MM-DDTHH:mm:ss",
				  "amount": 0.0,
				  "description": "...",
				  "type": "DEBIT | CREDIT",
				  "account": "...",
				  "currency": "...",
				  "category": "...",
				}

				Rules:
				- Use ISO 8601 format for date: YYYY-MM-DDTHH:mm:ss
				- Amount must be numeric (no commas, no currency symbols).
				- "description" must contain ONLY the merchant/beneficiary name (e.g. "SHASHIKALA KUMARI"). followed by any UPI/reference/transaction IDs.
				- If data is missing, Set it as Unknown.
				- account must extract as much detail as possible including any card/account numbers.
				- any UPI/reference/transaction IDs should be appended to end of description included in description.
				          """;
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("model", "gpt-oss-20b");
		requestBody.put("prompt", "can you classify transaction data for:" + prompt);
		requestBody.put("messages",
				List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", prompt)));
		HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
		if (StringUtils.isEmpty(host)) {
			host = "http://10.0.0.100:1234";
		}
		logger.info("host: {}", host);
		ResponseEntity<String> response = restTemplate.exchange(host + "/v1/chat/completions", HttpMethod.POST,
				requestEntity, String.class);

		return response.getBody();
	}

	public void getGptResponse(String emailContent, AccountTransaction accountTransaction) {
		try {
			logger.info("Email Content: {}", emailContent);
			accountTransaction.setRawData(emailContent);
			String response = callOpenAI(emailContent);
			logger.info("OpenAI Response: {}", response);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(response);
			logger.info("Parsed JSON Root: {}", root.toString());
			JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
			logger.info("Extracted Content: {}", contentNode.toString());
			JsonNode parsedContent;
			if (contentNode.isTextual()) {
				parsedContent = mapper.readTree(contentNode.asText());
			} else {
				parsedContent = contentNode;
			}
			// Set GPT fields
			JsonNode gptAmountNode = parsedContent.get("amount");
			if (gptAmountNode != null && !gptAmountNode.isNull()) {
				try {
					accountTransaction.setGptAmount(new BigDecimal(gptAmountNode.asText()));
				} catch (NumberFormatException e) {
					logger.warn("Invalid GPT amount: {}", gptAmountNode.asText());
					accountTransaction.setGptAmount(null);
				}
			} else {
				accountTransaction.setGptAmount(null);
			}

			JsonNode gptDescriptionNode = parsedContent.get("description");
			if (gptDescriptionNode != null && !gptDescriptionNode.isNull()) {
				accountTransaction.setGptDescription(gptDescriptionNode.asText());
			} else {
				accountTransaction.setGptDescription(null);
			}

			JsonNode gptExplanationNode = parsedContent.get("explanation");
			if (gptExplanationNode != null && !gptExplanationNode.isNull()) {
				accountTransaction.setGptExplanation(gptExplanationNode.asText());
			} else {
				accountTransaction.setGptExplanation(null);
			}

			JsonNode gptTypeNode = parsedContent.get("type");
			if (gptTypeNode != null && !gptTypeNode.isNull()) {
				try {
					accountTransaction.setGptType(TransactionType.valueOf(gptTypeNode.asText()));
				} catch (IllegalArgumentException e) {
					logger.warn("Invalid GPT type: {}", gptTypeNode.asText());
					accountTransaction.setGptType(null);
				}
			} else {
				accountTransaction.setGptType(null);
			}

			JsonNode currencyNode = parsedContent.get("currency");
			if (currencyNode != null && !currencyNode.isNull()) {
				accountTransaction.setCurrency(currencyNode.asText());
			} else {
				accountTransaction.setCurrency(null);
			}
			JsonNode accountNode = parsedContent.get("account");
			String accountDetail = null;
			String originalAccountDetail = null;
			if (accountNode != null && !accountNode.isNull()) {
				originalAccountDetail = accountNode.asText();
				accountDetail = originalAccountDetail;
				if ("Unknown".equalsIgnoreCase(accountDetail)) {
					accountDetail = null;
				}
			}
			logger.info("Extracted Account Detail: {}", accountDetail);
			
			// Only proceed with fuzzy matching if we have account detail to match
			if (accountDetail != null && !accountDetail.trim().isEmpty()) {
				List<AccountDTO> accounts = accountService.getAllAccounts(accountTransaction.getAppUser());
				String normalizedAccountDetail = normalize(accountDetail);
				
				// Skip fuzzy matching if normalized detail is empty
				if (normalizedAccountDetail.isEmpty()) {
					logger.warn("Account detail became empty after normalization: '{}'", accountDetail);
					return;
				}
				
				logger.info("Normalized Account Detail for matching: '{}'", normalizedAccountDetail);
				int highestScore = 0;
				AccountDTO bestMatch = null;
				
				for(AccountDTO account : accounts) {
				String accName = normalize(account.name());
				String accType = normalize(account.accountType().name());
				String instName = normalize(account.institution().name());
				String accNumber = normalize(account.accountNumber());
				String rawData = normalize(accountTransaction.getRawData());
				String description = normalize(accountTransaction.getDescription());
				
				int score = safeFuzzyScore(normalizedAccountDetail, accName);
				score += safeFuzzyScore(normalizedAccountDetail, accType);
				score += safeFuzzyScore(normalizedAccountDetail, instName);
				score += safeFuzzyScore(normalize(account.accountType().name()), rawData);
				score += safeFuzzyScore(normalize(account.institution().name()), description);
				
				// Enhanced matching with new identifier fields
				if (account.accountNumber() != null && !account.accountNumber().trim().isEmpty()) {
					// High weight for account number matches
					score += safeFuzzyScore(normalizedAccountDetail, accNumber) * 3;
					score += safeFuzzyScore(accNumber, rawData) * 3;
					score += safeFuzzyScore(accNumber, description) * 2;
				}
				
				if (account.accountKeywords() != null && !account.accountKeywords().trim().isEmpty()) {
					// Check each keyword separately for better matching
					String[] keywords = account.accountKeywords().split(",");
					for (String keyword : keywords) {
						String normalizedKeyword = normalize(keyword.trim());
						if (!normalizedKeyword.isEmpty()) {
							score += safeFuzzyScore(normalizedAccountDetail, normalizedKeyword) * 2;
							score += safeFuzzyScore(normalizedKeyword, rawData) * 2;
							score += safeFuzzyScore(normalizedKeyword, description) * 2;
						}
					}
				}
				
				if (account.accountAliases() != null && !account.accountAliases().trim().isEmpty()) {
					// Check each alias separately
					String[] aliases = account.accountAliases().split(",");
					for (String alias : aliases) {
						String normalizedAlias = normalize(alias.trim());
						if (!normalizedAlias.isEmpty()) {
							score += safeFuzzyScore(normalizedAccountDetail, normalizedAlias) * 2;
							score += safeFuzzyScore(normalizedAlias, rawData) * 2;
							score += safeFuzzyScore(normalizedAlias, description) * 2;
						}
					}
				}
				
				logger.info("Enhanced fuzzy scores for account '{}': nameScore={}, typeScore={}, instScore={}, numberScore={}, totalScore={}",
						account.name(), 
						safeFuzzyScore(normalizedAccountDetail, accName),
						safeFuzzyScore(normalizedAccountDetail, accType),
						safeFuzzyScore(normalizedAccountDetail, instName),
						account.accountNumber() != null ? safeFuzzyScore(normalizedAccountDetail, accNumber) : 0,
						score);
				
				// Lower threshold to be more inclusive, and always track the highest score
				if(score > highestScore) {
					highestScore = score;
					bestMatch = account;
				}
			}
				// Only set GPT account if we have a reasonable match (minimum score of 5)
				if(bestMatch != null && highestScore >= 5) {
					logger.info("Best matching account: {} with score {}", bestMatch.name(), highestScore);
					Account matchedAccount = accountService.getAccountByName(bestMatch.name(), accountTransaction.getAppUser());
					accountTransaction.setGptAccount(matchedAccount);
				} else if (bestMatch != null) {
					logger.warn("Best match '{}' has low score {} for GPT account detail: '{}' (minimum: 5)", 
						bestMatch.name(), highestScore, normalizedAccountDetail);
				} else {
					logger.warn("No account matched for GPT account detail: '{}'", normalizedAccountDetail);
				}
			} else {
				logger.info("No account detail extracted from GPT response, skipping fuzzy matching");
			}
					
		} catch (JsonProcessingException e) {
			logger.error("Error processing JSON response: {}", e.getMessage());

		}

	}

	private static String normalize(String input) {
		if (input == null)
			return "";
		return input.toLowerCase().replaceAll("[^a-z0-9 ]", " ") // remove punctuation
				.replaceAll("\\s+", " ") // collapse spaces
				.trim();
	}

	private static int safeFuzzyScore(String term1, String term2) {
		if (term1 == null || term2 == null || term1.isEmpty() || term2.isEmpty()) {
			return 0;
		}
		try {
			return fuzzyScore.fuzzyScore(term1, term2);
		} catch (Exception e) {
			// Log and return 0 if fuzzy scoring fails
			logger.warn("Fuzzy scoring failed for '{}' vs '{}': {}", term1, term2, e.getMessage());
			return 0;
		}
	}

}
