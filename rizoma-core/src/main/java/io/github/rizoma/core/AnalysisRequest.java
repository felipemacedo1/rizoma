package io.github.rizoma.core;

import java.util.Objects;

/** Complete input to one stateless analysis execution. */
public record AnalysisRequest(TabularSource source, TargetSchema targetSchema,
                              AnalysisOptions options) {
    public AnalysisRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetSchema, "targetSchema");
        options = options == null ? AnalysisOptions.defaults() : options;
    }
}
