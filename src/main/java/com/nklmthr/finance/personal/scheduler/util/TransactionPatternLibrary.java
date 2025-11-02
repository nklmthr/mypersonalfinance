package com.nklmthr.finance.personal.scheduler.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nklmthr.finance.personal.enums.TransactionType;

/**
 * Centralized library of regex patterns for extracting transaction data from bank emails.
 * Reduces code duplication and makes it easier to add support for new banks.
 * 
 * Pattern Naming Convention:
 * - AMOUNT_* : Patterns for extracting monetary amounts
 * - MERCHANT_* : Patterns for extracting merchant/payee names
 * - ACCOUNT_* : Patterns for extracting account identifiers
 * - UPI_* : Patterns for UPI-specific information
 */
public class TransactionPatternLibrary {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionPatternLibrary.class);
    
    // ==================== AMOUNT EXTRACTION PATTERNS ====================
    
    /**
     * Comprehensive list of amount patterns covering Indian bank formats.
     * Patterns are tried in order until a match is found.
     */
    private static final List<AmountPattern> AMOUNT_PATTERNS = List.of(
        // Format: "Total refund ₹1,057.30" - Amazon refund emails (highest priority for refunds)
        new AmountPattern("AMOUNT_TOTAL_REFUND", 
            Pattern.compile("Total refund\\s*₹\\s*([\\d,]+\\.?\\d*)"), 96),
        
        // Standard format: "Transaction Amount: INR 340.50"
        new AmountPattern("AMOUNT_STANDARD_INR", 
            Pattern.compile("Transaction Amount[:\\s]*INR\\s*([\\d,]+\\.?\\d*)"), 95),
        
        // Format: "credited with INR 340.50" - MUST come before AMOUNT_WITH_INR for specificity
        new AmountPattern("AMOUNT_CREDITED_WITH_INR", 
            Pattern.compile("(?:debited|credited) with INR\\s*([\\d,]+\\.?\\d*)"), 92),
        
        // Format: "INR 340.50 spent"
        new AmountPattern("AMOUNT_INR_PREFIX", 
            Pattern.compile("INR\\s*([\\d,]+\\.?\\d*)\\s+(?:spent|debited|credited|paid)"), 90),
        
        // Format: "for INR 340.50"
        new AmountPattern("AMOUNT_FOR_INR", 
            Pattern.compile("for INR\\s*([\\d,]+\\.?\\d*)"), 85),
        
        // Format: "Rs.340.00" or "Rs 340"
        new AmountPattern("AMOUNT_RS_PREFIX", 
            Pattern.compile("Rs\\.?\\s*([\\d,]+\\.?\\d*)"), 85),
        
        // Format: "₹340.50"
        new AmountPattern("AMOUNT_RUPEE_SYMBOL", 
            Pattern.compile("₹\\s*([\\d,]+\\.?\\d*)"), 85),
        
        // Format: "Amount: INR 340.50" or "Amount = 340.50"
        new AmountPattern("AMOUNT_LABELED", 
            Pattern.compile("Amount[:\\s=]+(?:INR|Rs\\.?)?\\s*([\\d,]+\\.?\\d*)"), 85),
        
        // Format: "Amount Debited: INR 340.50"
        new AmountPattern("AMOUNT_DEBITED", 
            Pattern.compile("Amount (?:Debited|Credited)[:\\s]+INR\\s*([\\d,]+\\.?\\d*)"), 90),
        
        // Format: "340.50 has been debited" - Lower priority to avoid matching account numbers
        new AmountPattern("AMOUNT_REVERSE", 
            Pattern.compile("([\\d,]+\\.\\d{2})\\s+has been (?:debited|credited)"), 75),
        
        // Generic: Any amount with INR/Rs nearby
        new AmountPattern("AMOUNT_GENERIC", 
            Pattern.compile("(?:INR|Rs\\.?)\\s*([\\d,]+\\.\\d{2})"), 70)
    );
    
    // ==================== MERCHANT/DESCRIPTION EXTRACTION PATTERNS ====================
    
    private static final List<DescriptionPattern> MERCHANT_PATTERNS = List.of(
        // Format: "your order: ORDER_NUMBER" - Amazon format (highest priority for Amazon emails)
        new DescriptionPattern("AMAZON_ORDER", 
            Pattern.compile("your order[:\\s]+([\\d\\-]+)", Pattern.CASE_INSENSITIVE), 98),
        
        // Format: "reference number is: REF_NUMBER" - Amazon refund format
        new DescriptionPattern("REFUND_REFERENCE", 
            Pattern.compile("refund reference number (?:is|:)[:\\s]+([\\d]+)", Pattern.CASE_INSENSITIVE), 97),
        
        // Format: "Merchant Name: MERCHANT" or "Merchant: MERCHANT" - Check this first (highest confidence)
        new DescriptionPattern("MERCHANT_LABELED", 
            Pattern.compile("\\bMerchant(?:\\s+Name|\\s+ID)?[:\\s]*:([A-Za-z0-9\\s&\\-]+?)(?=\\s+(?:Date|Axis|Card|on|\\d{2}/\\d{2}/\\d{4})|\\r?\\n|$)", Pattern.CASE_INSENSITIVE), 95),
        
        // Format: "Info: MERCHANT_NAME" - ICICI format
        new DescriptionPattern("INFO_LABELED", 
            Pattern.compile("Info[:\\s]+([A-Za-z0-9\\s&\\-\\.]+?)(?:\\.|$)"), 92),
        
        // Format: "at MERCHANT_NAME" or "at ATM-WDL/AXPR/246" - Higher priority than BY_PAYEE
        new DescriptionPattern("MERCHANT_AT_ON", 
            Pattern.compile("\\bat\\s+([A-Za-z0-9\\s&\\-\\./*]+?)(?:\\s+on\\s+\\d|\\s*\\.|$)"), 91),
        
        // Format: "Transaction Info: DESCRIPTION"
        new DescriptionPattern("TRANSACTION_INFO", 
            Pattern.compile("Transaction Info[:\\s]+([A-Za-z0-9\\s/\\-]+)"), 90),
        
        // Format: "by NEFT/RTGS/IMPS/PAYEE_NAME" - Exclude common phrases like "by you"
        new DescriptionPattern("BY_PAYEE", 
            Pattern.compile("\\bby\\s+(?!you\\b)([A-Za-z0-9\\s&\\-/]+?)(?:\\s+on|\\s*\\.|$)"), 85),
        
        // Format: "UPI/P2A/123456/MERCHANT_NAME"
        new DescriptionPattern("UPI_MERCHANT", 
            Pattern.compile("UPI/[^/]+/[^/]+/([A-Za-z0-9@\\s]+)"), 90),
        
        // Format: "Reference no. - REFERENCE"
        new DescriptionPattern("REFERENCE_NO", 
            Pattern.compile("Reference (?:no\\.|No\\.|number)[:\\s\\-]+([^\\s\\.]+)"), 80),
        
        // Format: "Description = TEXT"
        new DescriptionPattern("DESCRIPTION_LABELED", 
            Pattern.compile("Description[:\\s=]+([^\r\n]+)"), 85)
    );
    
    // ==================== ACCOUNT IDENTIFIER PATTERNS ====================
    
    private static final List<Pattern> ACCOUNT_PATTERNS = List.of(
        // Format: "A/c no. 123456789" - Try full account number first
        Pattern.compile("A/c (?:no\\.|number)[:\\s]*(\\d{6,})"),
        
        // Format: "ending with 2804" or "ending in 2804"
        Pattern.compile("ending (?:with|in)\\s+(\\d{4})"),
        
        // Format: "XX2804"
        Pattern.compile("XX(\\d{4})"),
        
        // Format: "Credit Card no. XX1234"
        Pattern.compile("Credit Card (?:no\\.|number)[:\\s]*(?:XX)?(\\d{4})")
    );
    
    // ==================== PUBLIC API METHODS ====================
    
    /**
     * Extract transaction amount from email content.
     * Tries multiple patterns in order of confidence.
     * 
     * @param content Email content
     * @return PatternResult with amount and confidence score
     */
    public static PatternResult<BigDecimal> extractAmount(String content) {
        if (content == null || content.isEmpty()) {
            return PatternResult.empty();
        }
        
        for (AmountPattern ap : AMOUNT_PATTERNS) {
            Matcher m = ap.pattern.matcher(content);
            if (m.find()) {
                try {
                    String amountStr = m.group(1).replace(",", "");
                    BigDecimal amount = new BigDecimal(amountStr);
                    logger.debug("Extracted amount {} using pattern {}", amount, ap.name);
                    return PatternResult.withConfidence(amount, ap.confidence, ap.name);
                } catch (NumberFormatException e) {
                    logger.debug("Failed to parse amount from matched string: {}", m.group(1));
                    continue;
                }
            }
        }
        
        logger.warn("No amount pattern matched in content");
        return PatternResult.empty();
    }
    
    /**
     * Extract merchant name or transaction description.
     * 
     * @param content Email content
     * @return PatternResult with description and confidence score
     */
    public static PatternResult<String> extractDescription(String content) {
        if (content == null || content.isEmpty()) {
            return PatternResult.empty();
        }
        
        for (DescriptionPattern dp : MERCHANT_PATTERNS) {
            Matcher m = dp.pattern.matcher(content);
            if (m.find()) {
                String description = cleanDescription(m.group(1));
                if (!description.isEmpty()) {
                    logger.debug("Extracted description '{}' using pattern {}", description, dp.name);
                    return PatternResult.withConfidence(description, dp.confidence, dp.name);
                }
            }
        }
        
        logger.warn("No description pattern matched in content");
        return PatternResult.empty();
    }
    
    /**
     * Extract account identifier (last 4 digits, account number, etc.)
     * 
     * @param content Email content
     * @return Optional account identifier
     */
    public static Optional<String> extractAccountIdentifier(String content) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        
        for (Pattern pattern : ACCOUNT_PATTERNS) {
            Matcher m = pattern.matcher(content);
            if (m.find()) {
                String accountId = m.group(1);
                logger.debug("Extracted account identifier: {}", accountId);
                return Optional.of(accountId);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Detect transaction type (DEBIT/CREDIT) from email keywords.
     * 
     * @param content Email content
     * @return TransactionType (defaults to DEBIT if ambiguous)
     */
    public static TransactionType detectTransactionType(String content) {
        if (content == null || content.isEmpty()) {
            return TransactionType.DEBIT; // Default
        }
        
        String lowerContent = content.toLowerCase();
        
        // Credit keywords (money received)
        List<String> creditKeywords = List.of(
            "credited", "credit notification", "credit transaction",
            "received", "refund", "cashback", "reward",
            "deposit", "amount credited", "has been credited"
        );
        
        // Debit keywords (money spent)
        List<String> debitKeywords = List.of(
            "debited", "debit notification", "debit transaction",
            "spent", "paid", "purchase", "transaction amount",
            "withdrawn", "amount debited", "has been debited"
        );
        
        // Count matches for each type
        int creditScore = 0;
        int debitScore = 0;
        
        for (String keyword : creditKeywords) {
            if (lowerContent.contains(keyword)) {
                creditScore++;
            }
        }
        
        for (String keyword : debitKeywords) {
            if (lowerContent.contains(keyword)) {
                debitScore++;
            }
        }
        
        // Return type with higher score
        if (creditScore > debitScore) {
            logger.debug("Detected CREDIT transaction (score: {} vs {})", creditScore, debitScore);
            return TransactionType.CREDIT;
        } else {
            logger.debug("Detected DEBIT transaction (score: {} vs {})", debitScore, creditScore);
            return TransactionType.DEBIT;
        }
    }
    
    /**
     * Extract UPI reference number or transaction ID.
     * 
     * @param content Email content
     * @return Optional UPI reference
     */
    public static Optional<String> extractUpiReference(String content) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        
        // Format: "UPI Ref 123456789"
        Pattern upiRefPattern = Pattern.compile("UPI Ref[:\\s]*(\\d+)");
        Matcher m = upiRefPattern.matcher(content);
        if (m.find()) {
            String ref = m.group(1);
            logger.debug("Extracted UPI reference: {}", ref);
            return Optional.of(ref);
        }
        
        // Format: "UPI/P2A/123456/..."
        Pattern upiIdPattern = Pattern.compile("UPI/[^/]+/(\\d+)");
        m = upiIdPattern.matcher(content);
        if (m.find()) {
            String ref = m.group(1);
            logger.debug("Extracted UPI ID: {}", ref);
            return Optional.of(ref);
        }
        
        return Optional.empty();
    }
    
    /**
     * Try multiple patterns to extract amount with fallback.
     * Returns the first successful match.
     * 
     * @param content Email content
     * @param customPatterns Additional bank-specific patterns to try first
     * @return Optional amount
     */
    public static Optional<BigDecimal> extractAmountWithFallback(String content, Pattern... customPatterns) {
        // Try custom patterns first
        for (Pattern pattern : customPatterns) {
            Matcher m = pattern.matcher(content);
            if (m.find()) {
                try {
                    String amountStr = m.group(1).replace(",", "");
                    return Optional.of(new BigDecimal(amountStr));
                } catch (Exception e) {
                    continue;
                }
            }
        }
        
        // Fall back to standard patterns
        PatternResult<BigDecimal> result = extractAmount(content);
        return result.isPresent() ? Optional.of(result.getValue()) : Optional.empty();
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Clean extracted description by removing common noise.
     */
    private static String cleanDescription(String raw) {
        if (raw == null) return "";
        
        String cleaned = raw.trim();
        
        // Remove common footer patterns (more aggressive matching)
        List<String> stopWords = List.of(
            "Regards", "Call us at", "Always open", "***", 
            "Reach us at", "For any concerns", "If ", "Please ",
            "Customer Service"
        );
        
        for (String stopWord : stopWords) {
            int index = cleaned.indexOf(stopWord);
            if (index != -1) {
                cleaned = cleaned.substring(0, index).trim();
                break;
            }
        }
        
        // Remove trailing punctuation and whitespace
        cleaned = cleaned.replaceAll("[\\s\\.\\,\\;]+$", "");
        
        return cleaned;
    }
    
    // ==================== INTERNAL PATTERN CLASSES ====================
    
    /**
     * Internal class to store amount patterns with metadata
     */
    private static class AmountPattern {
        final String name;
        final Pattern pattern;
        final int confidence;
        
        AmountPattern(String name, Pattern pattern, int confidence) {
            this.name = name;
            this.pattern = pattern;
            this.confidence = confidence;
        }
    }
    
    /**
     * Internal class to store description patterns with metadata
     */
    private static class DescriptionPattern {
        final String name;
        final Pattern pattern;
        final int confidence;
        
        DescriptionPattern(String name, Pattern pattern, int confidence) {
            this.name = name;
            this.pattern = pattern;
            this.confidence = confidence;
        }
    }
}

