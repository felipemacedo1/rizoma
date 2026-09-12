package io.github.felipemacedo1.rizoma.core;

import java.util.Objects;

/** Request-scoped input to bounded layout recognition. */
public record LayoutRecognitionRequest(TabularSource source, TargetSchema targetSchema,
        AnalysisOptions options, LayoutRegistry registry, int guardSampleRows) {
    public LayoutRecognitionRequest {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(targetSchema, "targetSchema");
        options = options == null ? AnalysisOptions.defaults() : options;
        registry = registry == null ? NoOpLayoutRegistry.INSTANCE : registry;
        if (guardSampleRows <= 0 || guardSampleRows > 10_000)
            throw new IllegalArgumentException("guardSampleRows must be in [1,10000]");
    }

    /** Default bounded guard of 64 data rows. */
    public LayoutRecognitionRequest(TabularSource source, TargetSchema targetSchema,
            AnalysisOptions options, LayoutRegistry registry) {
        this(source, targetSchema, options, registry, 64);
    }
}
