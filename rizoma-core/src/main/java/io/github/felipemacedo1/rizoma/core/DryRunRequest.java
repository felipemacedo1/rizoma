package io.github.felipemacedo1.rizoma.core;

import java.util.Objects;

/** Complete input for one read-only dry-run execution. */
public record DryRunRequest(TabularSource source, TargetSchema targetSchema, MappingPlan plan,
                            AnalysisOptions analysisOptions, DryRunOptions dryRunOptions) {
    public DryRunRequest {
        Objects.requireNonNull(source); Objects.requireNonNull(targetSchema); Objects.requireNonNull(plan);
        analysisOptions = analysisOptions == null ? AnalysisOptions.defaults() : analysisOptions;
        dryRunOptions = dryRunOptions == null ? DryRunOptions.defaults() : dryRunOptions;
    }
}
