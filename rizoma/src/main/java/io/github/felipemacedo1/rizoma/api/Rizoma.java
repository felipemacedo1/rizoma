package io.github.felipemacedo1.rizoma.api;

import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.AnalysisRequest;
import io.github.felipemacedo1.rizoma.core.AnalysisResult;
import io.github.felipemacedo1.rizoma.core.BuiltInTransformers;
import io.github.felipemacedo1.rizoma.core.BuiltInValidators;
import io.github.felipemacedo1.rizoma.core.CoreSemanticDetectors;
import io.github.felipemacedo1.rizoma.core.DataReader;
import io.github.felipemacedo1.rizoma.core.DryRunRequest;
import io.github.felipemacedo1.rizoma.core.DryRunResult;
import io.github.felipemacedo1.rizoma.core.EngineConfig;
import io.github.felipemacedo1.rizoma.core.EngineException;
import io.github.felipemacedo1.rizoma.core.HeaderNormalizer;
import io.github.felipemacedo1.rizoma.core.InMemoryLayoutRegistry;
import io.github.felipemacedo1.rizoma.core.LayoutCompatibilityReport;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionRequest;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionResult;
import io.github.felipemacedo1.rizoma.core.LayoutRegistry;
import io.github.felipemacedo1.rizoma.core.LayoutTemplate;
import io.github.felipemacedo1.rizoma.core.MappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import io.github.felipemacedo1.rizoma.core.MappingPlanner;
import io.github.felipemacedo1.rizoma.core.NoOpLayoutRegistry;
import io.github.felipemacedo1.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.SemanticDetector;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import io.github.felipemacedo1.rizoma.core.TargetField;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import io.github.felipemacedo1.rizoma.core.Validator;
import io.github.felipemacedo1.rizoma.core.ValueTransformer;
import io.github.felipemacedo1.rizoma.csv.CsvDataReader;
import io.github.felipemacedo1.rizoma.excel.ExcelDataReader;
import io.github.felipemacedo1.rizoma.ptbr.PtBrDetectors;
import io.github.felipemacedo1.rizoma.ptbr.PtBrExecutionRules;
import io.github.felipemacedo1.rizoma.ptbr.PtBrHeaderRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Public adoption facade with safe CSV/XLS/XLSX and pt-BR defaults.
 *
 * <p>The facade and its requests/results are immutable and may be reused
 * sequentially. Concurrent calls are not guaranteed because custom readers,
 * detectors, transformers, validators, observers and stores can be stateful.
 * A caller sharing one instance concurrently must ensure every supplied
 * extension is thread-safe.</p>
 */
@Experimental
public final class Rizoma {
    private final io.github.felipemacedo1.rizoma.core.MappingEngine engine;
    private final MappingPlanner planner = new MappingPlanner();
    private final HeaderNormalizer normalizer;
    private final MappingKnowledgeBase defaultKnowledge;
    private final LayoutRegistry defaultLayouts;
    private final ProcessObserver defaultObserver;

    private Rizoma(Builder builder) {
        normalizer = builder.normalizer;
        defaultKnowledge = builder.knowledgeBase;
        defaultLayouts = builder.layoutRegistry;
        defaultObserver = builder.observer;
        engine = io.github.felipemacedo1.rizoma.core.MappingEngine.builder()
                .readers(builder.readers)
                .semanticDetectors(builder.detectors)
                .transformers(builder.transformers)
                .validators(builder.validators)
                .normalizer(builder.normalizer)
                .configuration(builder.configuration)
                .build();
    }

    /** Creates a ready-to-use facade with safe deterministic defaults. */
    public static Rizoma create() { return builder().build(); }

    /** Starts advanced composition while retaining all standard defaults. */
    public static Builder builder() { return new Builder(); }

    /**
     * Executes the safe high-level workflow. Mapping suggestions are never
     * treated as confirmation: without a plan or reusable template the result
     * is {@link ProcessStatus#REVIEW_REQUIRED}.
     */
    public ProcessResult process(ProcessRequest request) {
        Objects.requireNonNull(request, "request");
        ProcessObserver observer = request.observer().orElse(defaultObserver);
        try {
            emit(observer, ProcessObserver.Stage.STARTED, "PROCESS_STARTED",
                    Map.of("sourceId", request.source().id(), "schemaId", request.schema().id()));
            ProcessResult result = processInternal(request, observer);
            emit(observer, ProcessObserver.Stage.COMPLETED, result.status().name(),
                    Map.of("route", result.route().name()));
            return result;
        } catch (EngineException exception) {
            return failed(observer, exception.code(), exception.getMessage());
        } catch (RizomaException exception) {
            return failed(observer, exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            return failed(observer, "PROCESSING_FAILED",
                    "processing failed without exposing source or adapter internals");
        }
    }

    private ProcessResult processInternal(ProcessRequest request, ProcessObserver observer) {
        if (request.mappingPlan().isPresent()) {
            emitRoute(observer, ProcessRoute.CONFIRMED_PLAN);
            DryRunResult dryRun = engine.dryRun(new DryRunRequest(request.source(), request.schema(),
                    request.mappingPlan().orElseThrow(), request.analysisOptions(), request.dryRunOptions()));
            return fromDryRun(ProcessRoute.CONFIRMED_PLAN, null, null,
                    request.mappingPlan().orElseThrow(), dryRun, request.schema());
        }
        if (request.executionPreference() == ProcessRequest.ExecutionPreference.ANALYZE_ONLY) {
            emitRoute(observer, ProcessRoute.FULL_ANALYSIS);
            return fromAnalysis(analyzeRaw(request), null, request.schema());
        }

        LayoutRegistry registry = registryFor(request);
        if (registry != NoOpLayoutRegistry.INSTANCE) {
            LayoutRecognitionResult recognition = engine.recognizeLayout(new LayoutRecognitionRequest(
                    request.source(), request.schema(), request.analysisOptions(), registry));
            ProcessRoute route = route(recognition.route());
            emitRoute(observer, route);
            if (recognition.mappingPlan() != null) {
                DryRunResult dryRun = engine.dryRun(new DryRunRequest(request.source(), request.schema(),
                        recognition.mappingPlan(), request.analysisOptions(), request.dryRunOptions()));
                return fromDryRun(route, null, recognition, recognition.mappingPlan(), dryRun, request.schema());
            }
            return fromAnalysis(analyzeRaw(request), recognition, request.schema());
        }

        emitRoute(observer, ProcessRoute.FULL_ANALYSIS);
        return fromAnalysis(analyzeRaw(request), null, request.schema());
    }

    private AnalysisResult analyzeRaw(ProcessRequest request) {
        MappingKnowledgeBase knowledge = request.knowledgeBase().orElse(defaultKnowledge);
        return engine.analyze(new AnalysisRequest(request.source(), request.schema(),
                request.analysisOptions(), knowledge));
    }

    private LayoutRegistry registryFor(ProcessRequest request) {
        LayoutRegistry registry = request.layoutRegistry().orElse(defaultLayouts);
        if (request.layoutTemplate().isEmpty()) return registry;
        var direct = new InMemoryLayoutRegistry(1);
        direct.register(request.layoutTemplate().orElseThrow());
        if (registry == NoOpLayoutRegistry.INSTANCE) return direct;
        return new CompositeLayoutRegistry(direct, registry);
    }

    private ProcessResult fromAnalysis(AnalysisResult analysis,
            LayoutRecognitionResult recognition, TargetSchema schema) {
        Map<String, String> headers = new LinkedHashMap<>();
        analysis.structure().columns().forEach(column -> headers.put(column.id(), column.header()));
        var suggestions = analysis.decisionsByColumn().values().stream()
                .sorted(Comparator.comparing(AnalysisResult.MappingDecision::sourceColumnId))
                .map(decision -> new ProcessResult.MappingSuggestion(decision.sourceColumnId(),
                        headers.getOrDefault(decision.sourceColumnId(), ""), decision.targetFieldId(),
                        decision.status().name(), decision.score(), decision.coverage(), decision.margin(),
                        decision.reasons(), decision.blockers())).toList();
        int suggested = (int) suggestions.stream().filter(item -> !item.targetFieldId().isBlank()).count();
        int required = (int) schema.fields().stream().filter(TargetField::required).count();
        var summary = new ProcessResult.MappingSummary(suggested, 0, 0,
                analysis.unmatchedColumns().size(), required, suggestions);
        var profile = new ProcessResult.ProfilingSummary(analysis.rowsProcessed(),
                analysis.profiles().size(), analysis.warnings().size(), true);
        var warnings = new ArrayList<ProcessResult.ProcessIssue>();
        analysis.warnings().forEach(value -> warnings.add(
                new ProcessResult.ProcessIssue("ANALYSIS_WARNING", value, 1)));
        analysis.conflicts().forEach(value -> warnings.add(
                new ProcessResult.ProcessIssue("MAPPING_CONFLICT", value, 1)));
        var errors = analysis.errors().stream().map(error ->
                new ProcessResult.ProcessIssue(error.code(), error.message(), 1)).toList();
        return new ProcessResult(ProcessStatus.REVIEW_REQUIRED, ProcessRoute.FULL_ANALYSIS,
                summary, profile, analysis, recognition, null, null, warnings, errors);
    }

    private ProcessResult fromDryRun(ProcessRoute route, AnalysisResult analysis,
            LayoutRecognitionResult recognition, MappingPlan plan,
            DryRunResult dryRun, TargetSchema schema) {
        ProcessStatus status = dryRun.rowsInvalid() > 0 || dryRun.rowsSkipped() > 0
                || dryRun.totalErrorCount() > 0 || dryRun.terminatedEarly()
                ? ProcessStatus.INVALID
                : dryRun.rowsValidWithWarnings() > 0 || dryRun.totalWarningCount() > 0
                    ? ProcessStatus.SUCCESS_WITH_WARNINGS : ProcessStatus.SUCCESS;
        int projected = (int) plan.mappings().stream().filter(mapping ->
                mapping.projectionSource().kind() != io.github.felipemacedo1.rizoma.core.ProjectionSource.Kind.UNMAPPED).count();
        var projectedTargets = plan.mappings().stream().filter(mapping ->
                mapping.projectionSource().kind() != io.github.felipemacedo1.rizoma.core.ProjectionSource.Kind.UNMAPPED)
                .map(MappingPlan.FieldMapping::targetFieldId).collect(java.util.stream.Collectors.toSet());
        int unresolvedRequired = (int) schema.fields().stream().filter(TargetField::required)
                .filter(field -> !projectedTargets.contains(field.id())).count();
        var summary = new ProcessResult.MappingSummary(0, projected,
                plan.ignoredSourceColumns().size(), plan.unmappedSourceColumns().size(),
                unresolvedRequired, List.of());
        var warnings = dryRun.warningCodes().entrySet().stream()
                .map(entry -> new ProcessResult.ProcessIssue(entry.getKey(),
                        "dry run reported a data warning", entry.getValue())).toList();
        var errors = dryRun.errorCodes().entrySet().stream()
                .map(entry -> new ProcessResult.ProcessIssue(entry.getKey(),
                        "dry run reported a data error", entry.getValue())).toList();
        return new ProcessResult(status, route, summary,
                ProcessResult.ProfilingSummary.empty(), analysis, recognition,
                plan, dryRun, warnings, errors);
    }

    private ProcessResult failed(ProcessObserver observer, String code, String message) {
        safeFailureEvent(observer, code);
        return new ProcessResult(ProcessStatus.FAILED, ProcessRoute.NOT_STARTED,
                ProcessResult.MappingSummary.empty(), ProcessResult.ProfilingSummary.empty(),
                null, null, null, null, List.of(),
                List.of(new ProcessResult.ProcessIssue(code, message, 1)));
    }

    /** Runs the detailed workflow analysis and translates engine errors to public exceptions. */
    public AnalysisResult analyze(AnalysisRequest request) {
        try { return engine.analyze(request); }
        catch (EngineException exception) { throw translate(exception); }
    }

    /** Convenience detailed analysis using facade defaults for options and knowledge. */
    public AnalysisResult analyze(TabularSource source, TargetSchema schema) {
        return analyze(new AnalysisRequest(source, schema, AnalysisOptions.defaults(), defaultKnowledge));
    }

    /** Creates a direct-column plan from explicit source-to-target confirmations. */
    public MappingPlan plan(AnalysisResult analysis, TargetSchema schema,
            Map<String, String> sourceToTarget, List<String> ignoredSourceColumns) {
        var selections = sourceToTarget.entrySet().stream().map(entry ->
                new MappingPlanner.ProjectionSelection(entry.getValue(),
                        io.github.felipemacedo1.rizoma.core.ProjectionSource.sourceColumn(entry.getKey()),
                        "explicit public API confirmation")).toList();
        try { return planner.createProjected(analysis, schema, selections, ignoredSourceColumns); }
        catch (EngineException exception) { throw translate(exception); }
        catch (IllegalArgumentException exception) {
            throw new IncompatiblePlanException("INVALID_PLAN_CONFIGURATION", exception.getMessage());
        }
    }

    /** Creates an advanced projected plan from explicit confirmations. */
    public MappingPlan planProjected(AnalysisResult analysis, TargetSchema schema,
            List<MappingPlanner.ProjectionSelection> selections, List<String> ignoredSourceColumns) {
        try { return planner.createProjected(analysis, schema, selections, ignoredSourceColumns); }
        catch (EngineException exception) { throw translate(exception); }
        catch (IllegalArgumentException exception) {
            throw new IncompatiblePlanException("INVALID_PLAN_CONFIGURATION", exception.getMessage());
        }
    }

    /** Reconfigures transformer and validator steps for one projected target. */
    public MappingPlan configurePlan(MappingPlan plan, String targetFieldId,
            List<MappingPlan.Step> transformations, List<MappingPlan.Step> validations,
            String confirmationReason) {
        try {
            return planner.configureTarget(plan, targetFieldId, transformations, validations,
                    confirmationReason);
        } catch (IllegalArgumentException exception) {
            throw new IncompatiblePlanException("INVALID_PLAN_CONFIGURATION", exception.getMessage());
        }
    }

    /** Creates an immutable reusable layout recipe through the configured normalizer. */
    public LayoutTemplate createLayoutTemplate(String name, String templateVersion,
            AnalysisResult analysis, MappingPlan plan, String createdAt, String provenance) {
        try {
            return LayoutTemplate.create(name, templateVersion, analysis, plan,
                    createdAt, provenance, normalizer);
        } catch (EngineException exception) {
            throw translate(exception);
        } catch (IllegalArgumentException exception) {
            throw new IncompatiblePlanException("INVALID_TEMPLATE_CONFIGURATION", exception.getMessage());
        }
    }

    /** Runs detailed bounded layout recognition. */
    public LayoutRecognitionResult recognizeLayout(LayoutRecognitionRequest request) {
        try { return engine.recognizeLayout(request); }
        catch (EngineException exception) { throw translate(exception); }
    }

    /** Runs the detailed read-only dry-run workflow. */
    public DryRunResult dryRun(DryRunRequest request) {
        try { return engine.dryRun(request); }
        catch (EngineException exception) { throw translate(exception); }
    }

    private static RizomaException translate(EngineException exception) {
        String code = exception.code();
        if (code.contains("PLAN") || code.equals("INVALIDATE_PLAN") || code.equals("REQUIRE_REANALYSIS"))
            return new IncompatiblePlanException(code, exception.getMessage());
        if (code.contains("SCHEMA")) return new InvalidSchemaException(code, exception.getMessage());
        if (code.contains("SOURCE") || code.equals("UNSUPPORTED_SOURCE") || code.equals("RECORD_LIMIT")
                || code.equals("FIELD_LIMIT"))
            return new InvalidSourceException(code, exception.getMessage());
        return new ProcessingException(code, exception.getMessage());
    }

    private static ProcessRoute route(LayoutCompatibilityReport.ExecutionRoute route) {
        return switch (route) {
            case FULL_ANALYSIS -> ProcessRoute.FULL_ANALYSIS;
            case FAST_REUSE -> ProcessRoute.FAST_REUSE;
            case ADAPTIVE_REANALYSIS -> ProcessRoute.ADAPTIVE_REANALYSIS;
        };
    }

    private static void emitRoute(ProcessObserver observer, ProcessRoute route) {
        emit(observer, ProcessObserver.Stage.ROUTE_SELECTED, route.name(), Map.of("route", route.name()));
    }

    private static void emit(ProcessObserver observer, ProcessObserver.Stage stage,
            String code, Map<String, String> metadata) {
        try { observer.onEvent(new ProcessObserver.ProcessEvent(stage, code, metadata)); }
        catch (RuntimeException exception) {
            throw new ProcessingException("OBSERVER_FAILED", "process observer failed safely");
        }
    }

    private static void safeFailureEvent(ProcessObserver observer, String code) {
        try { observer.onEvent(new ProcessObserver.ProcessEvent(
                ProcessObserver.Stage.FAILED, code, Map.of())); }
        catch (RuntimeException ignored) { /* failure reporting must not replace the original safe result */ }
    }

    /** Advanced facade composition. Standard format and pt-BR rules are present by default. */
    public static final class Builder {
        private final List<DataReader> readers = new ArrayList<>(List.of(
                new ExcelDataReader(), new CsvDataReader()));
        private final List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
        private final List<ValueTransformer<?, ?>> transformers = new ArrayList<>(BuiltInTransformers.defaults());
        private final List<Validator<?>> validators = new ArrayList<>(BuiltInValidators.defaults());
        private EngineConfig configuration = EngineConfig.defaults();
        private HeaderNormalizer normalizer = PtBrHeaderRules.normalizer();
        private MappingKnowledgeBase knowledgeBase = NoOpMappingKnowledgeBase.INSTANCE;
        private LayoutRegistry layoutRegistry = NoOpLayoutRegistry.INSTANCE;
        private ProcessObserver observer = ProcessObserver.noop();

        private Builder() {
            detectors.addAll(PtBrDetectors.defaults());
            transformers.addAll(PtBrExecutionRules.transformers());
            validators.addAll(PtBrExecutionRules.validators());
        }
        /** Replaces format readers for advanced composition. */
        public Builder readers(List<DataReader> values) { readers.clear(); readers.addAll(values); return this; }
        /** Adds one semantic detector SPI implementation. */
        public Builder addSemanticDetector(SemanticDetector value) { detectors.add(Objects.requireNonNull(value)); return this; }
        /** Adds one value transformer SPI implementation. */
        public Builder addTransformer(ValueTransformer<?, ?> value) { transformers.add(Objects.requireNonNull(value)); return this; }
        /** Adds one validator SPI implementation. */
        public Builder addValidator(Validator<?> value) { validators.add(Objects.requireNonNull(value)); return this; }
        /** Replaces scoring and resource limits; auto-map remains the caller's explicit responsibility. */
        public Builder configuration(EngineConfig value) { configuration = Objects.requireNonNull(value); return this; }
        /** Replaces header normalization for advanced/domain-specific usage. */
        public Builder normalizer(HeaderNormalizer value) { normalizer = Objects.requireNonNull(value); return this; }
        /** Sets default request-scoped historical evidence. */
        public Builder knowledgeBase(MappingKnowledgeBase value) { knowledgeBase = Objects.requireNonNull(value); return this; }
        /** Sets the default known-layout registry. */
        public Builder layoutRegistry(LayoutRegistry value) { layoutRegistry = Objects.requireNonNull(value); return this; }
        /** Sets a synchronous safe lifecycle observer. */
        public Builder observer(ProcessObserver value) { observer = Objects.requireNonNull(value); return this; }
        /** Builds an immutable facade for sequential reuse. */
        public Rizoma build() { return new Rizoma(this); }
    }

    private record CompositeLayoutRegistry(LayoutRegistry primary, LayoutRegistry secondary)
            implements LayoutRegistry {
        @Override public List<LayoutTemplate> findCandidates(LayoutQuery query) {
            var values = new LinkedHashMap<String, LayoutTemplate>();
            primary.findCandidates(query).forEach(value -> values.put(value.templateId() + '@'
                    + value.templateVersion(), value));
            secondary.findCandidates(query).forEach(value -> values.putIfAbsent(value.templateId() + '@'
                    + value.templateVersion(), value));
            return List.copyOf(values.values());
        }
        @Override public void register(LayoutTemplate template) {
            throw new UnsupportedOperationException("composite request registry is read-only");
        }
        @Override public java.util.Optional<LayoutTemplate> get(String templateId, String templateVersion) {
            return primary.get(templateId, templateVersion)
                    .or(() -> secondary.get(templateId, templateVersion));
        }
    }
}
