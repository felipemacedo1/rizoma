package io.github.felipemacedo1.rizoma.core;

/** Bounded deterministic summary of feedback for one exact source/target context. */
public record HistoricalEvidence(String targetFieldId, String normalizedSourceName,
        long confirmedCount, long rejectedCount, long correctedToCount, long correctedFromCount,
        String lastPositiveAt, String lastNegativeAt, double historicalScore,
        double reliability, boolean conflicting, String explanation) {
    public HistoricalEvidence {
        if (targetFieldId == null || targetFieldId.isBlank())
            throw new IllegalArgumentException("targetFieldId must not be blank");
        if (normalizedSourceName == null || normalizedSourceName.isBlank())
            throw new IllegalArgumentException("normalizedSourceName must not be blank");
        if (confirmedCount < 0 || rejectedCount < 0 || correctedToCount < 0 || correctedFromCount < 0)
            throw new IllegalArgumentException("historical counts must not be negative");
        if (!unit(historicalScore) || !unit(reliability))
            throw new IllegalArgumentException("historical score and reliability must be in [0,1]");
        lastPositiveAt = lastPositiveAt == null ? "" : lastPositiveAt;
        lastNegativeAt = lastNegativeAt == null ? "" : lastNegativeAt;
        explanation = explanation == null ? "" : explanation;
    }

    /** Total positive events, including corrections toward this target. */
    public long positiveCount() { return confirmedCount + correctedToCount; }

    /** Total negative events, including corrections away from this target. */
    public long negativeCount() { return rejectedCount + correctedFromCount; }

    /** Whether any matching feedback exists. */
    public boolean available() { return positiveCount() + negativeCount() > 0; }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
