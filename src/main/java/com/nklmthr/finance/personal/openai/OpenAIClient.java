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

    public static void main(String[] args) {
        OpenAIClient impl = new OpenAIClient();
        impl.host = "http://localhost:1234";
        impl.gptModel = "nuextract-v1.5";
        impl.apiKey = "empty";
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
        headers.set("Authorization", "Bearer " + apiKey);

        String systemPrompt = """
                Extract transaction from bank email using these patterns:

                AMOUNT: Find "Rs.340.00" or "INR 250.00" → extract 340.0
                DESCRIPTION: Find merchant after "at" or after "UPI/P2A/numbers/" → extract "SHASHIKALAKUMARI" or "Ganesh S N"
                ACCOUNT: Find "XX2804" or "ending with 2606" → extract "XX2804" or "2606"
                DATE: Find "28-09-25, 12:48:34" or "18-09-25" → convert to "2025-09-28T12:48:34"
                TYPE: "spent" or "Debited" = DEBIT, "Credited" = CREDIT

                Examples:
                Email: "Rs.340.00 spent on SBI Credit Card ending with 2606 at SHASHIKALAKUMARI on 18-09-25"
                JSON: {"id":null,"date":"2025-09-18T00:00:00","amount":340.0,"description":"SHASHIKALAKUMARI","type":"DEBIT","account":"2606","currency":"INR","category":"Unknown"}

                Email: "Amount Credited: INR 2.00 Account Number: XX2804 UPI/P2A/101541316204/NPCI BHIM"
                JSON: {"id":null,"date":"2025-09-25T18:15:47","amount":2.0,"description":"NPCI BHIM","type":"CREDIT","account":"XX2804","currency":"INR","category":"Unknown"}

                Return only JSON.
                      """;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", gptModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", prompt)
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "transaction_extraction");
        schema.put("schema", Map.of(
            "type", "object",
            "properties", Map.of(
                "id", Map.of("type", "string", "nullable", true),
                "date", Map.of(
                    "type", "string",
                    "description", "ISO 8601 date"
                ),
                "amount", Map.of("type", "number"),
                "description", Map.of(
                    "type", "string",
                    "description", "where money was spent"
                ),
                "type", Map.of(
                    "type", "string",
                    "enum", List.of("DEBIT", "CREDIT"),
                    "description", "is the money spent (DEBIT) or recieved(CREDIT)"
                ),
                "account", Map.of("type", "string", "description", "identifying the account from which transaction is done like Axis, SBI, ICICI, CSB bank including any account numbers"),
                "currency", Map.of("type", "string", "description", "Currency of the transaction like INR, EUR, USD"),
                "category", Map.of("type", "string")
            ),
            "required", List.of("date", "amount", "description", "type", "account", "currency")
        ));


        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", schema);

        requestBody.put("response_format", responseFormat);

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
