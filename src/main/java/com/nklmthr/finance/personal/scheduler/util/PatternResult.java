package com.nklmthr.finance.personal.scheduler.util;

/**
 * Wrapper for pattern extraction results with confidence scoring.
 * Helps identify which pattern matched and how confident we are in the result.
 */
public class PatternResult<T> {
    private final T value;
    private final int confidenceScore; // 0-100
    private final String matchedPattern; // Pattern identifier for debugging

    private PatternResult(T value, int confidenceScore, String matchedPattern) {
        this.value = value;
        this.confidenceScore = confidenceScore;
        this.matchedPattern = matchedPattern;
    }

    /**
     * Create a result with high confidence (90)
     */
    public static <T> PatternResult<T> of(T value, String patternName) {
        return new PatternResult<>(value, 90, patternName);
    }

    /**
     * Create a result with custom confidence score
     */
    public static <T> PatternResult<T> withConfidence(T value, int confidence, String patternName) {
        return new PatternResult<>(value, confidence, patternName);
    }

    /**
     * Create an empty result (no match found)
     */
    public static <T> PatternResult<T> empty() {
        return new PatternResult<>(null, 0, "NO_MATCH");
    }

    public T getValue() {
        return value;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public String getMatchedPattern() {
        return matchedPattern;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean hasHighConfidence() {
        return confidenceScore >= 80;
    }

    @Override
    public String toString() {
        return String.format("PatternResult{value=%s, confidence=%d, pattern=%s}", 
            value, confidenceScore, matchedPattern);
    }
}

