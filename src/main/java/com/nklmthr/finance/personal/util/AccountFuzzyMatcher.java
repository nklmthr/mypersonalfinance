package com.nklmthr.finance.personal.util;

import java.util.List;

import org.apache.commons.text.similarity.FuzzyScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nklmthr.finance.personal.dto.AccountDTO;

/**
 * Utility class for fuzzy matching accounts based on transaction data.
 * Uses account attributes like name, number, keywords, and aliases to find the best match.
 */
@Component
public class AccountFuzzyMatcher {

    private static final Logger logger = LoggerFactory.getLogger(AccountFuzzyMatcher.class);
    private static final FuzzyScore fuzzyScore = new FuzzyScore(java.util.Locale.ENGLISH);
    private static final int MINIMUM_MATCH_SCORE = 5;
    private static final int EXACT_MATCH_BONUS = 10; // Bonus for exact substring matches

    /**
     * Match result containing the matched account and confidence score
     */
    public record MatchResult(AccountDTO account, int score) {
        public boolean isValid() {
            return account != null && score >= MINIMUM_MATCH_SCORE;
        }
    }

    /**
     * Find the best matching account for given transaction data.
     * 
     * @param accounts List of all available accounts
     * @param accountDetail Optional account detail from GPT or other source
     * @param rawData The raw transaction email content
     * @param description The extracted transaction description
     * @return MatchResult with best match and score
     */
    public MatchResult findBestMatch(
            List<AccountDTO> accounts, 
            String accountDetail,
            String rawData, 
            String description) {

        String normalizedAccountDetail = normalize(accountDetail);
        String normalizedRawData = normalize(rawData);
        String normalizedDescription = normalize(description);

        int highestScore = 0;
        AccountDTO bestMatch = null;

        for (AccountDTO account : accounts) {
            int score = calculateAccountScore(
                account, 
                normalizedAccountDetail, 
                normalizedRawData, 
                normalizedDescription
            );

            if (score > highestScore) {
                highestScore = score;
                bestMatch = account;
            }
        }

        logMatchResult(bestMatch, highestScore, accountDetail);
        return new MatchResult(bestMatch, highestScore);
    }

    /**
     * Simplified version that matches based on raw data and description only.
     * Useful for extraction services that don't have GPT account detail.
     */
    public MatchResult findBestMatch(
            List<AccountDTO> accounts,
            String rawData,
            String description) {
        return findBestMatch(accounts, null, rawData, description);
    }

    /**
     * Calculate match score for a single account.
     */
    private int calculateAccountScore(
            AccountDTO account,
            String normalizedAccountDetail,
            String rawData,
            String description) {

        int score = 0;

        // Match against basic account attributes
        String accName = normalize(account.name());
        String accType = normalize(account.accountType().name());
        String instName = normalize(account.institution().name());
        String accNumber = normalize(account.accountNumber());

        // Score based on account detail (if provided, e.g., from GPT)
        if (!normalizedAccountDetail.isEmpty()) {
            score += safeFuzzyScore(normalizedAccountDetail, accName);
            score += safeFuzzyScore(normalizedAccountDetail, accType);
            score += safeFuzzyScore(normalizedAccountDetail, instName) * 2; // Higher weight for institution
            // Bonus for exact institution match in account detail
            if (normalizedAccountDetail.contains(instName)) {
                score += EXACT_MATCH_BONUS * 2; // Double bonus for institution match
            }
        }

        // Score based on transaction data - institution match is critical
        score += safeFuzzyScore(accType, rawData);
        score += safeFuzzyScore(instName, rawData) * 3; // Much higher weight for institution in rawData
        score += safeFuzzyScore(instName, description) * 2; // Higher weight for institution
        
        // Critical: Bonus for exact institution name match
        if (rawData.contains(instName)) {
            score += EXACT_MATCH_BONUS * 3; // Triple bonus for institution in rawData
        }
        if (description.contains(instName)) {
            score += EXACT_MATCH_BONUS * 2; // Double bonus for institution in description
        }

        // Account number matching (higher weight)
        if (account.accountNumber() != null && !account.accountNumber().trim().isEmpty()) {
            if (!normalizedAccountDetail.isEmpty()) {
                score += safeFuzzyScore(normalizedAccountDetail, accNumber) * 3;
                // Bonus for exact match
                if (normalizedAccountDetail.contains(accNumber)) {
                    score += EXACT_MATCH_BONUS;
                }
            }
            score += safeFuzzyScore(accNumber, rawData) * 3;
            score += safeFuzzyScore(accNumber, description) * 2;
            // Bonus for exact matches in transaction data
            if (rawData.contains(accNumber)) {
                score += EXACT_MATCH_BONUS;
            }
            if (description.contains(accNumber)) {
                score += EXACT_MATCH_BONUS / 2;
            }
        }

        // Account keywords matching (medium weight)
        if (account.accountKeywords() != null && !account.accountKeywords().trim().isEmpty()) {
            for (String keyword : account.accountKeywords().split(",")) {
                String normalizedKeyword = normalize(keyword.trim());
                if (!normalizedKeyword.isEmpty()) {
                    if (!normalizedAccountDetail.isEmpty()) {
                        score += safeFuzzyScore(normalizedAccountDetail, normalizedKeyword) * 2;
                        // Bonus for exact match
                        if (normalizedAccountDetail.contains(normalizedKeyword)) {
                            score += EXACT_MATCH_BONUS;
                        }
                    }
                    score += safeFuzzyScore(normalizedKeyword, rawData) * 2;
                    score += safeFuzzyScore(normalizedKeyword, description) * 2;
                    // Bonus for exact matches in transaction data
                    if (rawData.contains(normalizedKeyword)) {
                        score += EXACT_MATCH_BONUS;
                    }
                    if (description.contains(normalizedKeyword)) {
                        score += EXACT_MATCH_BONUS / 2;
                    }
                }
            }
        }

        // Account aliases matching (medium weight)
        if (account.accountAliases() != null && !account.accountAliases().trim().isEmpty()) {
            for (String alias : account.accountAliases().split(",")) {
                String normalizedAlias = normalize(alias.trim());
                if (!normalizedAlias.isEmpty()) {
                    if (!normalizedAccountDetail.isEmpty()) {
                        score += safeFuzzyScore(normalizedAccountDetail, normalizedAlias) * 2;
                        // Bonus for exact match
                        if (normalizedAccountDetail.contains(normalizedAlias)) {
                            score += EXACT_MATCH_BONUS;
                        }
                    }
                    score += safeFuzzyScore(normalizedAlias, rawData) * 2;
                    score += safeFuzzyScore(normalizedAlias, description) * 2;
                    // Bonus for exact matches in transaction data
                    if (rawData.contains(normalizedAlias)) {
                        score += EXACT_MATCH_BONUS;
                    }
                    if (description.contains(normalizedAlias)) {
                        score += EXACT_MATCH_BONUS / 2;
                    }
                }
            }
        }

        return score;
    }

    /**
     * Normalize string for fuzzy matching by converting to lowercase and removing special characters.
     */
    private static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Safe fuzzy score calculation with error handling.
     */
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

    /**
     * Log the match result for debugging.
     */
    private void logMatchResult(AccountDTO bestMatch, int score, String accountDetail) {
        if (bestMatch != null && score >= MINIMUM_MATCH_SCORE) {
            logger.info("Best matching account: {} with score {}", bestMatch.name(), score);
        } else if (bestMatch != null) {
            logger.warn("Best match '{}' has low score {} (minimum: {}){}", 
                    bestMatch.name(), score, MINIMUM_MATCH_SCORE,
                    accountDetail != null ? " for account detail: '" + accountDetail + "'" : "");
        } else {
            logger.warn("No account matched{}",
                    accountDetail != null ? " for account detail: '" + accountDetail + "'" : "");
        }
    }
}

