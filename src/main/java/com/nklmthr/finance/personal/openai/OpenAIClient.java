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

    @Value("${openai.gpt-model-openAIHost}")
    private String openAIHost;

    @Value("${openai.gpt-model}")
    private String openAIModel;

    @Value("${openai.gpt-model-api-key}")
    private String openAIApiKey;

    @Value("${openai.organization-id:}")
    private String openAIOrganizationId;

    @Value("${openai.project-id:}")
    private String openAIProjectId;

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    private static final FuzzyScore fuzzyScore = new FuzzyScore(Locale.ENGLISH);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AccountService accountService;

    public static void main(String[] args) {
        OpenAIClient impl = new OpenAIClient();
		String envHost = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com");
		String envModel = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
		String envKey = "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";
		if (envKey == null || envKey.isBlank()) {
			// Fallbacks commonly used in this project and local shells
			envKey = System.getenv("prod_myFinance_key");
		}
		if (envKey == null || envKey.isBlank()) {
			// Allow Java system properties too
			envKey = System.getProperty("OPENAI_API_KEY", System.getProperty("prod_myFinance_key", ""));
		}
		if (envKey == null || envKey.isBlank()) {
			System.err.println("OPENAI_API_KEY not set. Set env var OPENAI_API_KEY (or prod_myFinance_key) before running.");
			return;
		}
		impl.openAIHost = envHost;
		impl.openAIModel = envModel;
		impl.openAIApiKey = envKey;
        String emailText =
                """
                28-09-2025 Dear Nikhil Mathur, Here's the summary of your transaction: Amount Debited: INR 250.00 Account Number: XX2804 Date & Time: 28-09-25, 12:48:34 IST Transaction Info: UPI/P2A/563748979856/Ganesh S N If this transaction was not initiated by you: To block UPI: SMS BLOCKUPI <Customer ID> to +919951860002 from your registered mobile number. Call us at: 18001035577 (Toll Free) 18604195555 (Charges Applicable) Always open to help you. Regards, Axis Bank Ltd. ****This is a system generated communication and does not require signature. **** E003598217_09_2024 Reach us at: CHAT WEB Support Mobile app INTERNET BANKING WHATSAPP BRANCH LOCATOR Copyright Axis Bank Ltd. All rights reserved. Terms & Conditions apply. Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTP with anyone, either over phone or through email. RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc. Don't be victim to such offers coming to you on phone or email in the name of RBI. Do not click on Links from unknown/unsecure Sources that seek your confidential information. This email is confidential. It may also be legally privileged. If you are not the addressee, you may not copy, forward, disclose or use any part of it. Internet communications cannot be guaranteed to be timely, secure, error or virus-free. The sender does not accept liability for any errors or omissions. We maintain strict security standards and procedures to prevent unauthorised access to information about you. Know more >> Untitled 28-09-2025 Dear Nikhil Mathur, Here's the summary of your transaction: Amount Debited: INR 250.00 Account Number: XX2804 Date & Time: 28-09-25, 12:48:34 IST Transaction Info: UPI/P2A/563748979856/Ganesh S N If this transaction was not initiated by you: To block UPI: SMS BLOCKUPI <Customer ID> to +919951860002 from your registered mobile number. Call us at: 18001035577 (Toll Free) 18604195555 (Charges Applicable) Always open to help you. Regards, Axis Bank Ltd. ****This is a system generated communication and does not require signature. **** E003598217_09_2024 Reach us at: CHAT WEB Support Mobile app INTERNET BANKING WHATSAPP BRANCH LOCATOR Copyright Axis Bank Ltd. All rights reserved. Terms & Conditions apply. Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTP with anyone, either over phone or through email. RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc. Don't be victim to such offers coming to you on phone or email in the name of RBI. Do not click on Links from unknown/unsecure Sources that seek your confidential information. This email is confidential. It may also be legally privileged. If you are not the addressee, you may not copy, forward, disclose or use any part of it. Internet communications cannot be guaranteed to be timely, secure, error or virus-free. The sender does not accept liability for any errors or omissions. We maintain strict security standards and procedures to prevent unauthorised access to information about you. Know more >>
                """;

        String response = impl.callOpenAI(emailText);
        System.out.println(response);
    }

    /**
     * Calls the OpenAI/OSS model and returns a pure JSON string
     */
    private String callOpenAI(String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openAIApiKey);
        if (openAIOrganizationId != null && !openAIOrganizationId.isBlank()) {
            headers.set("OpenAI-Organization", openAIOrganizationId);
        }
        if (openAIProjectId != null && !openAIProjectId.isBlank()) {
            headers.set("OpenAI-Project", openAIProjectId);
        }

        String systemPrompt = """
                You are a strict JSON generator. Return ONLY a single JSON object, no explanations, no markdown, no code fences.
                Extract transaction from bank email using these patterns:
                - AMOUNT: Find examples like "Rs.340.00" or "INR 250.00" → extract 340.0
                - DESCRIPTION: Merchant after "at" or after "UPI/P2A/numbers/" → e.g. "SHASHIKALAKUMARI" or "Ganesh S N"
                - ACCOUNT: "XX2804" or "ending with 2606" → extract "XX2804" or "2606"
                - DATE: e.g. "28-09-25, 12:48:34" or "18-09-25" → convert to ISO-8601 "2025-09-28T12:48:34"
                - TYPE: "spent"/"Debited" = DEBIT, "Credited" = CREDIT
                Output must be a single JSON object matching the required fields.
                """;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAIModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        ));

        // Use json_schema as required by OSS server (accepts 'json_schema' or 'text')
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "transaction_extraction");
		schema.put("schema", Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                "id", Map.of("type", "string", "nullable", true),
                "date", Map.of("type", "string", "description", "ISO 8601 date"),
                "amount", Map.of("type", "number"),
                "description", Map.of("type", "string", "description", "where money was spent"),
                "type", Map.of("type", "string", "enum", List.of("DEBIT", "CREDIT"), "description", "is the money spent (DEBIT) or recieved(CREDIT)"),
                "account", Map.of("type", "string", "description", "identifying the account from which transaction is done like Axis, SBI, ICICI, CSB bank including any account numbers"),
                "currency", Map.of("type", "string", "description", "Currency of the transaction like INR, EUR, USD"),
                "category", Map.of("type", "string")
            ),
			"required", List.of("id", "date", "amount", "description", "type", "account", "currency", "category")
        ));
        schema.put("strict", true);

        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", schema);
        requestBody.put("response_format", responseFormat);

        // Make responses deterministic and bounded
        requestBody.put("temperature", 0);
        requestBody.put("max_tokens", 512);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                openAIHost + "/v1/chat/completions",
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        try {
            JsonNode root = mapper.readTree(response.getBody());
            // Prefer tool/function arguments if provided (some servers use this)
            JsonNode toolArgs = root.path("choices").path(0).path("message").path("tool_calls").path(0).path("function").path("arguments");
            if (!toolArgs.isMissingNode() && !toolArgs.isNull()) {
                return toolArgs.toString();
            }
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return sanitizeToJson(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    /**
     * Attempts to coerce an LLM response into a pure JSON object string.
     * - Strips markdown code fences
     * - Trims whitespace
     * - Extracts the first {...} JSON object if extra text is present
     */
    private String sanitizeToJson(String content) {
        if (content == null) return "{}";
        String trimmed = content.trim();
        // Strip common code fences like ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```[a-zA-Z0-9]*\\n?", "").replaceAll("```$", "").trim();
        }
        // If still not a pure JSON object, try to extract the first {...}
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    /**
     * Consumes the GPT JSON response and enriches AccountTransaction
     */
    public void getGptResponse(String emailContent, AccountTransaction accountTransaction) {
        try {
            logger.info("Email Content: {}", emailContent);
            accountTransaction.setRawData(emailContent);

            String response = callOpenAI(emailContent);
            logger.info("OpenAI Response (raw/cleaned): {}", response);

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
            String rawGptDescription = null;
            JsonNode gptDescriptionNode = parsedContent.get("description");
            if (gptDescriptionNode != null && !gptDescriptionNode.isNull()) {
                rawGptDescription = gptDescriptionNode.asText();
            }

            accountTransaction.setGptDescription(rawGptDescription);

            TransactionType type = TransactionType.valueOf(parsedContent.get("type").asText());
            accountTransaction.setGptType(type);

            // ---- Currency ----
            JsonNode currencyNode = parsedContent.get("currency");
            accountTransaction.setCurrency(
                    currencyNode != null && !currencyNode.isNull() ? normalizeCurrency(currencyNode.asText()) : null
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
                accountTransaction.setGptAccount(accountTransaction.getAccount());
            }

        } catch (Exception e) {
            logger.error("Error processing GPT JSON response", e);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String c = currency.trim().toUpperCase();
        if (c.matches("(?i).*INR.*|.*RUP.*|.*RS\\.?")) return "INR";
        return currency;
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
