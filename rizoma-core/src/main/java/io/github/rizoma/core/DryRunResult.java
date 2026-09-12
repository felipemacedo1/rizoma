package io.github.rizoma.core;

import java.util.List;
import java.util.Map;

/** Bounded, privacy-safe report from the transformation and validation pipeline. */
public record DryRunResult(String formatVersion, String engineVersion, String planId,
        String sourceId, String sourceFingerprint, String schemaId, String schemaVersion,
        String schemaFingerprint, String configurationFingerprint,
        long rowsProcessed, long rowsValid, long rowsValidWithWarnings, long rowsInvalid,
        long rowsSkipped, long cellsProcessed, long nonEmptyCells, long charactersObserved,
        int fieldsMapped, long transformationsApplied, long validationsExecuted,
        long manualReviewRequiredCount, long totalErrorCount, long totalWarningCount,
        Map<String, Long> fieldErrors, Map<String, Long> errorCodes, Map<String, Long> warningCodes,
        List<RowIssue> issueSamples, boolean terminatedEarly, String terminationCode,
        long durationMillis) {
    public DryRunResult {
        fieldErrors = Map.copyOf(fieldErrors); errorCodes = Map.copyOf(errorCodes); warningCodes = Map.copyOf(warningCodes);
        issueSamples = List.copyOf(issueSamples); terminationCode = terminationCode == null ? "" : terminationCode;
    }

    /** Protected example of one field issue. */
    public record RowIssue(long recordNumber, long physicalLine, String sourceColumnId,
            String targetFieldId, String transformerId, String validatorId,
            String code, String reason, String protectedValue) {}
}
