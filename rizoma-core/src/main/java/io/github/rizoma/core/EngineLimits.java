package io.github.rizoma.core;

import java.time.Duration;
import java.util.Objects;

/** Explicit resource limits applied during analysis. */
public record EngineLimits(long maxBytes, long maxRecords, int maxColumns,
                           int maxFieldChars, int maxHeaderChars, int maxSamples,
                           int maxCandidates, int maxWarnings, int maxErrors,
                           Duration maxDuration, int maxTrackedDistinctValues,
                           int maxFrequentValues, int maxAnomalies,
                           int maxPrunedCandidateExplanations) {
    public EngineLimits {
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxBytes <= 0 || maxRecords <= 0 || maxColumns <= 0 || maxFieldChars <= 0
                || maxHeaderChars <= 0 || maxSamples < 0 || maxCandidates <= 0
                || maxWarnings <= 0 || maxErrors <= 0 || maxDuration.isNegative()
                || maxDuration.isZero() || maxTrackedDistinctValues <= 0
                || maxFrequentValues <= 0 || maxAnomalies <= 0
                || maxPrunedCandidateExplanations <= 0) {
            throw new IllegalArgumentException("all engine limits must be positive (samples may be zero)");
        }
    }

    /** Backward-compatible constructor using the bounded profiling defaults. */
    public EngineLimits(long maxBytes, long maxRecords, int maxColumns,
                        int maxFieldChars, int maxHeaderChars, int maxSamples,
                        int maxCandidates, int maxWarnings, int maxErrors,
                        Duration maxDuration) {
        this(maxBytes, maxRecords, maxColumns, maxFieldChars, maxHeaderChars,
                maxSamples, maxCandidates, maxWarnings, maxErrors, maxDuration,
                1_024, 10, 20, 100);
    }

    public static EngineLimits defaults() {
        return new EngineLimits(100L * 1024 * 1024, 10_000_000L, 1_000,
                1_000_000, 10_000, 20, 3, 100, 100, Duration.ofMinutes(10),
                1_024, 10, 20, 100);
    }
}
