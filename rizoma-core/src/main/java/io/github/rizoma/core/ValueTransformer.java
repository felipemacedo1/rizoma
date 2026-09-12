package io.github.rizoma.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed, deterministic value transformation. Data failures are returned, not thrown. */
public interface ValueTransformer<S, T> {
    String id();
    String version();
    Class<S> sourceType();
    Class<T> targetType();
    TransformationResult<T> transform(S value, TransformationContext context);

    /** Request-scoped transformation context. */
    record TransformationContext(TargetField targetField, String schemaLocale,
                                 Map<String, String> options) {
        public TransformationContext {
            Objects.requireNonNull(targetField); schemaLocale = schemaLocale == null ? "" : schemaLocale;
            options = Map.copyOf(options == null ? Map.of() : options);
        }
    }

    /** Result retaining the caller-provided original while reports use only protected projections. */
    record TransformationResult<T>(Status status, Object originalValue, T transformedValue,
            String transformerId, String transformerVersion, String code, String message,
            boolean lossy, boolean ambiguous, List<String> warnings) {
        public TransformationResult {
            Objects.requireNonNull(status); Objects.requireNonNull(transformerId);
            code = code == null ? "" : code; message = message == null ? "" : message;
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
        public static <T> TransformationResult<T> success(Object original, T value, String id, String version) {
            return new TransformationResult<>(Status.SUCCESS, original, value, id, version, "", "", false, false, List.of());
        }
        public static <T> TransformationResult<T> warning(Object original, T value, String id,
                String version, String code, String message, boolean lossy, boolean ambiguous) {
            return new TransformationResult<>(Status.WARNING, original, value, id, version,
                    code, message, lossy, ambiguous, List.of(code));
        }
        public static <T> TransformationResult<T> failure(Object original, String id,
                String version, String code, String message, boolean ambiguous) {
            return new TransformationResult<>(Status.FAILURE, original, null, id, version,
                    code, message, false, ambiguous, List.of());
        }
    }

    enum Status { SUCCESS, WARNING, FAILURE }
}
