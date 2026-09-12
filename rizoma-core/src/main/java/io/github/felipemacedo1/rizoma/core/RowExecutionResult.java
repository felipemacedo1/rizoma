package io.github.felipemacedo1.rizoma.core;

import java.util.List;

/** Transient, privacy-safe outcome for one row in the shared execution pipeline. */
public record RowExecutionResult(long recordNumber, long physicalLine, Status status,
                                 List<FieldResult> fields, List<String> warningCodes,
                                 List<String> errorCodes) {
    public RowExecutionResult {
        fields = List.copyOf(fields); warningCodes = List.copyOf(warningCodes); errorCodes = List.copyOf(errorCodes);
    }
    public enum Status { VALID, VALID_WITH_WARNINGS, INVALID, SKIPPED }
    /** Field-level execution metadata; no raw or transformed value is retained. */
    public record FieldResult(String sourceColumnId, String targetFieldId, Status status,
                              List<String> transformers, List<String> validators,
                              List<String> warningCodes, List<String> errorCodes,
                              String protectedValue) {
        public FieldResult {
            transformers = List.copyOf(transformers); validators = List.copyOf(validators);
            warningCodes = List.copyOf(warningCodes); errorCodes = List.copyOf(errorCodes);
        }
    }
}
