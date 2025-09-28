package com.nklmthr.finance.personal.openai;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    @Value("${gpt-model}")
    private String gptModel;

    @Value("${openai.api.key}")
    private String apiKey;

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    private static final FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AccountService accountService;

    /**
     * Calls the OpenAI/OSS model and returns a pure JSON string
     */
    private String callOpenAI(String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

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
              "category": "..."
            }

            Rules:
            - Use ISO 8601 format for date: YYYY-MM-DDTHH:mm:ss
            - Amount must be numeric (no commas, no currency symbols).
            - "description" must contain ONLY the merchant/beneficiary name followed by any UPI/reference/transaction IDs.
            - If data is missing, set it as "Unknown".
            - "account" must extract as much detail as possible including any card/account numbers.
            - Any UPI/reference/transaction IDs should be appended at the end of description.
        """;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", gptModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("response_format", Map.of("type", "json_object"));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                host + "/v1/chat/completions",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        try {
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    /**
     * Consumes the GPT JSON response and enriches AccountTransaction
     */
    public void getGptResponse(String emailContent, AccountTransaction accountTransaction) {
        try {
            logger.info("Email Content: {}", emailContent);
            accountTransaction.setRawData(emailContent);

            String response = callOpenAI(emailContent);
            logger.info("OpenAI Response JSON: {}", response);

            JsonNode parsedContent = mapper.readTree(response);

            // ---- Amount ----
            JsonNode gptAmountNode = parsedContent.get("amount");
            if (gptAmountNode != null && !gptAmountNode.isNull()) {
                try {
                    accountTransaction.setGptAmount(new BigDecimal(gptAmountNode.asText()));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid GPT amount: {}", gptAmountNode.asText());
                    accountTransaction.setGptAmount(null);
                }
            }

            // ---- Description ----
            JsonNode gptDescriptionNode = parsedContent.get("description");
            accountTransaction.setGptDescription(
                    gptDescriptionNode != null && !gptDescriptionNode.isNull() ? gptDescriptionNode.asText() : null
            );

            // ---- Type ----
            JsonNode gptTypeNode = parsedContent.get("type");
            if (gptTypeNode != null && !gptTypeNode.isNull()) {
                try {
                    accountTransaction.setGptType(TransactionType.valueOf(gptTypeNode.asText()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid GPT type: {}", gptTypeNode.asText());
                    accountTransaction.setGptType(null);
                }
            }

            // ---- Currency ----
            JsonNode currencyNode = parsedContent.get("currency");
            accountTransaction.setCurrency(
                    currencyNode != null && !currencyNode.isNull() ? currencyNode.asText() : null
            );

            // ---- Account Matching ----
            JsonNode accountNode = parsedContent.get("account");
            String accountDetail = (accountNode != null && !accountNode.isNull()) ? accountNode.asText() : null;
            if ("Unknown".equalsIgnoreCase(accountDetail)) {
                accountDetail = null;
            }

            logger.info("Extracted Account Detail: {}", accountDetail);

            if (accountDetail != null && !accountDetail.trim().isEmpty()) {
                matchAndSetAccount(accountDetail, accountTransaction);
            } else {
                logger.info("No account detail extracted from GPT response, skipping fuzzy matching");
            }

        } catch (Exception e) {
            logger.error("Error processing GPT JSON response", e);
        }
    }

    /**
     * Fuzzy matches GPT account detail with existing accounts
     */
    private void matchAndSetAccount(String accountDetail, AccountTransaction accountTransaction) {
        List<AccountDTO> accounts = accountService.getAllAccounts(accountTransaction.getAppUser());
        String normalizedAccountDetail = normalize(accountDetail);

        if (normalizedAccountDetail.isEmpty()) {
            logger.warn("Account detail became empty after normalization: '{}'", accountDetail);
            return;
        }

        int highestScore = 0;
        AccountDTO bestMatch = null;

        for (AccountDTO account : accounts) {
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

            if (account.accountNumber() != null && !account.accountNumber().trim().isEmpty()) {
                score += safeFuzzyScore(normalizedAccountDetail, accNumber) * 3;
                score += safeFuzzyScore(accNumber, rawData) * 3;
                score += safeFuzzyScore(accNumber, description) * 2;
            }

            if (account.accountKeywords() != null && !account.accountKeywords().trim().isEmpty()) {
                for (String keyword : account.accountKeywords().split(",")) {
                    String normalizedKeyword = normalize(keyword.trim());
                    if (!normalizedKeyword.isEmpty()) {
                        score += safeFuzzyScore(normalizedAccountDetail, normalizedKeyword) * 2;
                        score += safeFuzzyScore(normalizedKeyword, rawData) * 2;
                        score += safeFuzzyScore(normalizedKeyword, description) * 2;
                    }
                }
            }

            if (account.accountAliases() != null && !account.accountAliases().trim().isEmpty()) {
                for (String alias : account.accountAliases().split(",")) {
                    String normalizedAlias = normalize(alias.trim());
                    if (!normalizedAlias.isEmpty()) {
                        score += safeFuzzyScore(normalizedAccountDetail, normalizedAlias) * 2;
                        score += safeFuzzyScore(normalizedAlias, rawData) * 2;
                        score += safeFuzzyScore(normalizedAlias, description) * 2;
                    }
                }
            }

            if (score > highestScore) {
                highestScore = score;
                bestMatch = account;
            }
        }

        if (bestMatch != null && highestScore >= 5) {
            logger.info("Best matching account: {} with score {}", bestMatch.name(), highestScore);
            Account matchedAccount = accountService.getAccountByName(bestMatch.name(), accountTransaction.getAppUser());
            accountTransaction.setGptAccount(matchedAccount);
        } else if (bestMatch != null) {
            logger.warn("Best match '{}' has low score {} for GPT account detail: '{}' (minimum: 5)",
                    bestMatch.name(), highestScore, normalizedAccountDetail);
        } else {
            logger.warn("No account matched for GPT account detail: '{}'", normalizedAccountDetail);
        }
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase().replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int safeFuzzyScore(String term1, String term2) {
        if (term1 == null || term2 == null || term1.isEmpty() || term2.isEmpty()) {
            return 0;
        }
        try {
            return fuzzyScore.fuzzyScore(term1, term2);
        } catch (Exception e) {
            logger.warn("Fuzzy scoring failed for '{}' vs '{}': {}", term1, term2, e.getMessage());
            return 0;
        }
    }
}
