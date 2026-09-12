package io.github.felipemacedo1.rizoma.api;

import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.DryRunOptions;
import io.github.felipemacedo1.rizoma.core.LayoutRegistry;
import io.github.felipemacedo1.rizoma.core.LayoutTemplate;
import io.github.felipemacedo1.rizoma.core.MappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable high-level input for one process invocation. */
public final class ProcessRequest {
    private final TabularSource source;
    private final TargetSchema schema;
    private final AnalysisOptions analysisOptions;
    private final DryRunOptions dryRunOptions;
    private final MappingPlan mappingPlan;
    private final LayoutTemplate layoutTemplate;
    private final LayoutRegistry layoutRegistry;
    private final MappingKnowledgeBase knowledgeBase;
    private final ExecutionPreference executionPreference;
    private final ProcessObserver observer;

    private ProcessRequest(Builder builder) {
        source = Objects.requireNonNull(builder.source, "source");
        TargetSchema original = Objects.requireNonNull(builder.schema, "schema");
        schema = builder.locale == null || builder.locale.equals(original.locale()) ? original
                : new TargetSchema(original.id(), original.version(), original.context(),
                        builder.locale, original.fields());
        analysisOptions = builder.analysisOptions == null ? AnalysisOptions.defaults() : builder.analysisOptions;
        dryRunOptions = builder.dryRunOptions == null ? DryRunOptions.defaults() : builder.dryRunOptions;
        mappingPlan = builder.mappingPlan;
        layoutTemplate = builder.layoutTemplate;
        layoutRegistry = builder.layoutRegistry;
        knowledgeBase = builder.knowledgeBase;
        executionPreference = builder.executionPreference;
        observer = builder.observer;
        if (mappingPlan != null && layoutTemplate != null)
            throw new IncompatiblePlanException("AMBIGUOUS_PROCESS_RECIPE",
                    "mappingPlan and layoutTemplate are mutually exclusive");
        if (mappingPlan != null && executionPreference == ExecutionPreference.ANALYZE_ONLY)
            throw new IncompatiblePlanException("INCOMPATIBLE_EXECUTION_PREFERENCE",
                    "ANALYZE_ONLY cannot execute a confirmed mapping plan");
    }

    /** Creates the smallest request from a local file and schema. */
    public static ProcessRequest of(Path source, TargetSchema schema) {
        return builder(source, schema).build();
    }

    /** Creates the smallest request from an already reopenable source and schema. */
    public static ProcessRequest of(TabularSource source, TargetSchema schema) {
        return builder(source, schema).build();
    }

    /** Starts a request for a local path. */
    public static Builder builder(Path source, TargetSchema schema) {
        return new Builder(Sources.from(source), schema);
    }

    /** Starts a request for any reopenable Rizoma source. */
    public static Builder builder(TabularSource source, TargetSchema schema) {
        return new Builder(source, schema);
    }

    /** Reopenable read-only source. */
    public TabularSource source() { return source; }
    /** Effective target schema, including a request-level locale override. */
    public TargetSchema schema() { return schema; }
    /** Effective locale. */
    public String locale() { return schema.locale(); }
    /** Detailed reader and sampling options. */
    public AnalysisOptions analysisOptions() { return analysisOptions; }
    /** Bounded error policy used when dry run is possible. */
    public DryRunOptions dryRunOptions() { return dryRunOptions; }
    /** Optional source-bound plan that skips inference. */
    public Optional<MappingPlan> mappingPlan() { return Optional.ofNullable(mappingPlan); }
    /** Optional single confirmed layout recipe. */
    public Optional<LayoutTemplate> layoutTemplate() { return Optional.ofNullable(layoutTemplate); }
    /** Optional request-specific layout registry. */
    public Optional<LayoutRegistry> layoutRegistry() { return Optional.ofNullable(layoutRegistry); }
    /** Optional request-specific historical evidence source. */
    public Optional<MappingKnowledgeBase> knowledgeBase() { return Optional.ofNullable(knowledgeBase); }
    /** Selected high-level execution preference. */
    public ExecutionPreference executionPreference() { return executionPreference; }
    /** Optional request-specific observer. */
    public Optional<ProcessObserver> observer() { return Optional.ofNullable(observer); }

    /** Controls whether the facade may continue beyond analysis. */
    public enum ExecutionPreference {
        /** Recognize layouts and dry-run only when a confirmed recipe is available. */
        AUTO,
        /** Always perform full analysis and return its suggestions for review. */
        ANALYZE_ONLY
    }

    /** Builder whose defaults preserve abstention, bounded errors and no-op stores. */
    public static final class Builder {
        private final TabularSource source;
        private final TargetSchema schema;
        private String locale;
        private AnalysisOptions analysisOptions;
        private DryRunOptions dryRunOptions;
        private MappingPlan mappingPlan;
        private LayoutTemplate layoutTemplate;
        private LayoutRegistry layoutRegistry;
        private MappingKnowledgeBase knowledgeBase;
        private ExecutionPreference executionPreference = ExecutionPreference.AUTO;
        private ProcessObserver observer;

        private Builder(TabularSource source, TargetSchema schema) {
            this.source = Objects.requireNonNull(source, "source");
            this.schema = Objects.requireNonNull(schema, "schema");
        }
        /** Overrides the schema locale for this immutable request. */
        public Builder locale(String value) { locale = value == null ? "" : value; return this; }
        /** Sets format-neutral reader options and deterministic sampling seed. */
        public Builder analysisOptions(AnalysisOptions value) { analysisOptions = value; return this; }
        /** Sets bounded dry-run error policies. */
        public Builder dryRunOptions(DryRunOptions value) { dryRunOptions = value; return this; }
        /** Supplies an already confirmed source-bound plan and skips inference. */
        public Builder mappingPlan(MappingPlan value) { mappingPlan = value; return this; }
        /** Supplies one confirmed reusable layout recipe. */
        public Builder layoutTemplate(LayoutTemplate value) { layoutTemplate = value; return this; }
        /** Supplies an explicit layout registry; default comes from the facade. */
        public Builder layoutRegistry(LayoutRegistry value) { layoutRegistry = value; return this; }
        /** Supplies request-scoped historical evidence; default comes from the facade. */
        public Builder knowledgeBase(MappingKnowledgeBase value) { knowledgeBase = value; return this; }
        /** Selects automatic safe routing or analysis-only behavior. */
        public Builder executionPreference(ExecutionPreference value) {
            executionPreference = Objects.requireNonNull(value); return this;
        }
        /** Overrides the facade observer for this request. */
        public Builder observer(ProcessObserver value) { observer = value; return this; }
        /** Builds the immutable request. */
        public ProcessRequest build() { return new ProcessRequest(this); }
    }
}
