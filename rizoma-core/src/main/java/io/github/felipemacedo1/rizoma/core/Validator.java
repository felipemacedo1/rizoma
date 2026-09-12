package io.github.felipemacedo1.rizoma.core;

import java.util.Map;
import java.util.Objects;

/** Typed validation rule over a transient transformed value. */
public interface Validator<T> {
    String id();
    String version();
    Class<T> valueType();
    ValidationResult validate(T value, ValidationContext context);

    /** Request-scoped validation context; raw input is transient and never enters a report. */
    record ValidationContext(TargetField targetField, String schemaLocale,
                             String originalValue, Map<String, String> options) {
        public ValidationContext {
            Objects.requireNonNull(targetField); schemaLocale = schemaLocale == null ? "" : schemaLocale;
            originalValue = originalValue == null ? "" : originalValue;
            options = Map.copyOf(options == null ? Map.of() : options);
        }
    }

    /** Stable pass, warning or failure result. */
    record ValidationResult(Status status, String validatorId, String validatorVersion,
                            String code, String message) {
        public ValidationResult {
            Objects.requireNonNull(status); Objects.requireNonNull(validatorId);
            code = code == null ? "" : code; message = message == null ? "" : message;
        }
        public static ValidationResult pass(String id, String version) {
            return new ValidationResult(Status.PASS, id, version, "", "");
        }
        public static ValidationResult warning(String id, String version, String code, String message) {
            return new ValidationResult(Status.WARNING, id, version, code, message);
        }
        public static ValidationResult failure(String id, String version, String code, String message) {
            return new ValidationResult(Status.FAILURE, id, version, code, message);
        }
    }

    enum Status { PASS, WARNING, FAILURE }
}
