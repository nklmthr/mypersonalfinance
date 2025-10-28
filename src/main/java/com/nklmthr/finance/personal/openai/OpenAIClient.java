package com.nklmthr.finance.personal.openai;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher.MatchResult;

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
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private AccountFuzzyMatcher accountFuzzyMatcher;

    /**
     * Calls the OpenAI/OSS model and returns a pure JSON string
     */
    private String callOpenAI(String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        if (openAIApiKey == null || openAIApiKey.isBlank()) {
            logger.error("OpenAI API key is missing. Ensure 'PROD_MYFINANCE_KEY' (or property 'openai.gpt-model-api-key') is set.");
            throw new IllegalStateException("OpenAI API key is missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openAIApiKey);
        boolean hasOrgHeader = openAIOrganizationId != null && !openAIOrganizationId.isBlank();
        boolean hasProjectHeader = openAIProjectId != null && !openAIProjectId.isBlank();
        if (hasOrgHeader) {
            headers.set("OpenAI-Organization", openAIOrganizationId);
        }
        if (hasProjectHeader) {
            headers.set("OpenAI-Project", openAIProjectId);
        }

        String systemPrompt = """
                You are a strict JSON generator. Return ONLY a single JSON object, no explanations, no markdown, no code fences.
                Extract transaction from bank email using these patterns:
                - AMOUNT: Find examples like "Rs.340.00" or "INR 250.00" → extract 340.0
                - DESCRIPTION: Merchant after "at" or after "UPI/P2A/numbers/" → e.g. "SHASHIKALAKUMARI" or "Ganesh S N"
                - ACCOUNT: "XX2804" or "ending with 2606" → extract "XX2804" or "2606"
                - DATE: e.g. "28-09-25, 12:48:34" or "18-09-25" → convert to ISO-8601 "2025-09-28T12:48:34"
                - TYPE: Determine transaction type carefully:
                  * DEBIT = money spent/owed (purchases, payments, transfers out, debited from account)
                    - Credit card purchases at merchants = DEBIT
                    - Bank account debits/withdrawals = DEBIT
                    - Keywords: "Transaction Amount", "spent", "debited", "paid", "purchase", "merchant"
                  * CREDIT = money received/added (deposits, refunds, credits to account, salary)
                    - Keywords: "credited", "received", "refund", "cashback", "deposit"
                  * Default: If transaction shows merchant/purchase on credit card or bank account = DEBIT
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
        logger.info("Schema: {}", schema);
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", schema);
        requestBody.put("response_format", responseFormat);
        logger.info("Response Format: {}", responseFormat);
        // Make responses deterministic and bounded
        requestBody.put("temperature", 0);
        requestBody.put("max_tokens", 512);
        logger.info("Request Body: {}", requestBody);
        logger.info("OpenAI request setup - host: {}, model: {}, orgHeaderSet: {}, projectHeaderSet: {}",
                openAIHost, openAIModel, hasOrgHeader, hasProjectHeader);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        logger.debug("Authorization header set: {} (masked)", mask("Bearer " + openAIApiKey));
        logger.info("OpenAI Host: {}", openAIHost);
        logger.info("OpenAI Model: {}", openAIModel);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    openAIHost + "/v1/chat/completions",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                logger.error("OpenAI responded 401 Unauthorized. Verify API key and headers. model={}, host={}, orgHeaderSet={}, projectHeaderSet={}",
                        openAIModel, openAIHost, hasOrgHeader, hasProjectHeader);
            }
            throw e;
        }
        logger.info("Response: {}", response.getBody());
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
     * Note: rawData should already be set in the caller before invoking this method
     */
    public void getGptResponse(String emailContent, AccountTransaction accountTransaction) {
        try {
            logger.info("Email Content: {}", emailContent);
            // rawData is now set earlier in AbstractDataExtractionService to ensure it's captured for all transactions

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
            accountTransaction.setGptCurrency(
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
     * Fuzzy matches GPT account detail with existing accounts using AccountFuzzyMatcher utility
     */
    private void matchAndSetAccount(String accountDetail, AccountTransaction accountTransaction) {
        List<AccountDTO> accounts = accountService.getAllAccounts(accountTransaction.getAppUser());
        
        MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
            accounts,
            accountDetail,
            accountTransaction.getRawData(),
            accountTransaction.getDescription()
        );
        
        if (matchResult.isValid()) {
            Account matchedAccount = accountService.getAccountByName(
                matchResult.account().name(), 
                accountTransaction.getAppUser()
            );
            accountTransaction.setGptAccount(matchedAccount);
        }
    }

    private static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "<empty>";
        }
        int keep = Math.min(6, secret.length());
        return secret.substring(0, keep) + "***";
    }
}
