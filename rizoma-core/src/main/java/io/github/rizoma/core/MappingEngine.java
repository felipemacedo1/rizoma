package io.github.rizoma.core;

import io.github.rizoma.core.AnalysisResult.DecisionStatus;
import io.github.rizoma.core.AnalysisResult.MappingCandidate;
import io.github.rizoma.core.AnalysisResult.MappingDecision;
import io.github.rizoma.core.AnalysisResult.PrunedCandidate;
import io.github.rizoma.core.AnalysisResult.ScoreComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Stateless orchestrator for explainable analysis and dry runs. Instances are
 * safe for sequential reuse. Concurrent use is not guaranteed because supplied
 * readers, detectors, transformers and validators may have their own
 * thread-safety constraints.
 */
public final class MappingEngine {
    public static final String ENGINE_VERSION = "0.3.0-SNAPSHOT";
    private final List<DataReader> readers;
    private final List<SemanticDetector> detectors;
    private final EngineConfig config;
    private final HeaderNormalizer normalizer;
    private final ColumnFeatureExtractor featureExtractor;
    private final TransformationPipeline transformationPipeline;
    private final Map<String, Validator<?>> validators;
    private final String executionRegistryCanonical;
    private final SimilarityMetric dice = new DiceSimilarity();
    private final SimilarityMetric levenshtein = new LevenshteinSimilarity();
    private final SimilarityMetric jaccard = new JaccardSimilarity();
    private final SimilarityMetric jaro = new JaroSimilarity();
    private final SimilarityMetric jaroWinkler = new JaroWinklerSimilarity();
    private final SimilarityMetric ngram = new NGramSimilarity(3);
    private final SimilarityMetric cosine = new CosineSimilarity(3);

    private MappingEngine(Builder builder) {
        readers = List.copyOf(builder.readers);
        detectors = List.copyOf(builder.detectors);
        config = builder.config;
        normalizer = builder.normalizer;
        featureExtractor = new ColumnFeatureExtractor(normalizer);
        transformationPipeline = new TransformationPipeline(builder.transformers);
        var validatorMap = new LinkedHashMap<String, Validator<?>>();
        for (Validator<?> validator : builder.validators) {
            if (validatorMap.putIfAbsent(validator.id(), validator) != null)
                throw new IllegalArgumentException("duplicate validator: " + validator.id());
        }
        validators = Map.copyOf(validatorMap);
        var registry = new ArrayList<String>();
        builder.transformers.forEach(item -> registry.add("transformer:" + item.id() + '@' + item.version()));
        builder.validators.forEach(item -> registry.add("validator:" + item.id() + '@' + item.version()));
        registry.sort(String::compareTo);
        executionRegistryCanonical = registry.toString();
        if (readers.isEmpty()) throw new IllegalArgumentException("at least one reader is required");
        var types = new LinkedHashSet<SemanticType>();
        for (var detector : detectors) {
            if (!types.add(detector.type())) throw new IllegalArgumentException("duplicate detector: " + detector.type().id());
        }
    }

    /** Creates a builder whose default scoring policy keeps auto-map disabled. */
    public static Builder builder() { return new Builder(); }

    /** Analyzes without mutating the source or writing to a destination. */
    public AnalysisResult analyze(AnalysisRequest request) {
        Objects.requireNonNull(request, "request");
        long started = System.nanoTime();
        try {
            long size = request.source().size();
            if (size > config.limits().maxBytes()) {
                throw new EngineException("SOURCE_TOO_LARGE", "source exceeds configured byte limit");
            }
            DataReader reader = readers.stream().filter(r -> r.supports(request.source())).findFirst()
                    .orElseThrow(() -> new EngineException("UNSUPPORTED_SOURCE", "no reader supports this source"));
            String sourceFingerprint = request.source().sha256();
            var structure = reader.detect(request.source(), request.options(), config.limits());
            validateStructure(structure);
            var accumulators = new ArrayList<ProfileAccumulator>();
            for (int i = 0; i < structure.columns().size(); i++) {
                accumulators.add(new ProfileAccumulator(structure.columns().get(i), detectors,
                        config, request.options().sampleSeed() + i));
            }

            long rows = 0;
            var warnings = new ArrayList<>(structure.warnings());
            try (Dataset dataset = reader.open(request.source(), structure, request.options(), config.limits())) {
                var iterator = dataset.rows();
                while (iterator.hasNext()) {
                    checkExecution(started);
                    Dataset.Row row = iterator.next();
                    rows++;
                    if (rows > config.limits().maxRecords()) {
                        throw new EngineException("RECORD_LIMIT", "source exceeds configured record limit");
                    }
                    for (int i = 0; i < accumulators.size(); i++) {
                        accumulators.get(i).accept(row.values().get(i), row.recordNumber(), row.physicalLine());
                    }
                }
                warnings.addAll(dataset.warnings());
            }

            var profiles = accumulators.stream().map(ProfileAccumulator::finish).toList();
            String fingerprintAfterRead = request.source().sha256();
            if (!sourceFingerprint.equals(fingerprintAfterRead)) {
                throw new EngineException("SOURCE_CHANGED", "source content changed during analysis");
            }
            String schemaFingerprint = schemaFingerprint(request.targetSchema());
            String configFingerprint = configurationFingerprint(request.options());
            return score(request, structure, rows, profiles, warnings,
                    sourceFingerprint, schemaFingerprint, configFingerprint);
        } catch (EngineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("CANCELLED", "analysis was interrupted", e);
        } catch (Exception e) {
            throw new EngineException("ANALYSIS_FAILED", "analysis failed without exposing source data", e);
        }
    }

    /** Reopens a source and executes the configured plan without accepting any destination sink. */
    public DryRunResult dryRun(DryRunRequest request) {
        Objects.requireNonNull(request, "request");
        long started = System.nanoTime();
        try {
            if (request.source().size() > config.limits().maxBytes())
                throw new EngineException("SOURCE_TOO_LARGE", "source exceeds configured byte limit");
            validatePlan(request);
            DataReader reader = readers.stream().filter(item -> item.supports(request.source())).findFirst()
                    .orElseThrow(() -> new EngineException("UNSUPPORTED_SOURCE", "no reader supports this source"));
            DataReader.SourceStructure structure = reader.detect(request.source(), request.analysisOptions(), config.limits());
            validateStructure(structure);
            Map<String, Integer> positions = new HashMap<>();
            structure.columns().forEach(column -> positions.put(column.id(), column.position()));
            Map<String, TargetField> targets = new HashMap<>();
            request.targetSchema().fields().forEach(field -> targets.put(field.id(), field));
            for (var mapping : request.plan().mappings()) {
                if (!positions.containsKey(mapping.sourceColumnId()) || !targets.containsKey(mapping.targetFieldId()))
                    throw new EngineException("PLAN_STRUCTURE_MISMATCH", "mapping plan references an unavailable source or target field");
            }

            var counters = new DryRunCounters(request.dryRunOptions());
            try (Dataset dataset = reader.open(request.source(), structure, request.analysisOptions(), config.limits())) {
                var iterator = dataset.rows();
                while (iterator.hasNext() && !counters.terminated) {
                    checkExecution(started);
                    Dataset.Row row = iterator.next();
                    counters.rowsProcessed++;
                    if (counters.rowsProcessed > config.limits().maxRecords())
                        throw new EngineException("RECORD_LIMIT", "source exceeds configured record limit");
                    counters.cellsProcessed += row.values().size();
                    for (String raw : row.values()) {
                        if (raw != null && !raw.isBlank()) counters.nonEmptyCells++;
                        if (raw != null) counters.charactersObserved += raw.length();
                    }
                    RowExecutionResult result = executeRow(row, request, positions, targets, counters);
                    counters.acceptRow(result);
                }
            }
            String after = request.source().sha256();
            if (!request.plan().sourceFingerprint().equals(after))
                throw new EngineException("INVALIDATE_PLAN", "source content changed after the mapping plan was created");
            return counters.result(request, Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (EngineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("CANCELLED", "dry run was interrupted", e);
        } catch (Exception e) {
            throw new EngineException("DRY_RUN_FAILED", "dry run failed without exposing source data", e);
        }
    }

    private void validatePlan(DryRunRequest request) throws Exception {
        MappingPlan plan = request.plan();
        if (!plan.formatVersion().equals("1.0")) throw new EngineException("UNSUPPORTED_PLAN", "unsupported mapping plan format");
        if (!plan.engineVersion().equals(ENGINE_VERSION)) throw new EngineException("REQUIRE_REANALYSIS", "mapping plan engine version differs");
        String sourceFingerprint = request.source().sha256();
        if (!plan.sourceId().equals(request.source().id()) || !plan.sourceFingerprint().equals(sourceFingerprint))
            throw new EngineException("INVALIDATE_PLAN", "source identity or content differs from the analyzed source");
        if (!plan.schemaId().equals(request.targetSchema().id())
                || !plan.schemaVersion().equals(request.targetSchema().version())
                || !plan.schemaFingerprint().equals(schemaFingerprint(request.targetSchema())))
            throw new EngineException("REQUIRE_REANALYSIS", "target schema differs from the analyzed schema");
        if (!plan.configurationVersion().equals(config.version())
                || !plan.configurationFingerprint().equals(configurationFingerprint(request.analysisOptions())))
            throw new EngineException("REQUIRE_REANALYSIS", "engine or reader configuration differs from analysis");
        Set<String> mappedTargets = plan.mappings().stream()
                .map(MappingPlan.FieldMapping::targetFieldId).collect(java.util.stream.Collectors.toSet());
        if (request.targetSchema().fields().stream().anyMatch(field -> field.required() && !mappedTargets.contains(field.id())))
            throw new EngineException("INCOMPLETE_PLAN", "mapping plan leaves a required target field unmapped");
    }

    private RowExecutionResult executeRow(Dataset.Row row, DryRunRequest request,
            Map<String, Integer> positions, Map<String, TargetField> targets, DryRunCounters counters) {
        var fields = new ArrayList<RowExecutionResult.FieldResult>();
        var rowWarnings = new ArrayList<String>();
        var rowErrors = new ArrayList<String>();
        for (MappingPlan.FieldMapping mapping : request.plan().mappings()) {
            String raw = row.values().get(positions.get(mapping.sourceColumnId()));
            TargetField target = targets.get(mapping.targetFieldId());
            var transformerIds = new ArrayList<String>();
            var validatorIds = new ArrayList<String>();
            var warnings = new ArrayList<String>();
            var errors = new ArrayList<String>();
            Object transformed = raw;
            if (raw != null && !raw.isBlank()) {
                var transformedResult = transformationPipeline.execute(raw, mapping.transformations(), target,
                        request.targetSchema().locale());
                counters.transformationsApplied += transformedResult.steps().size();
                transformedResult.steps().forEach(step -> {
                    transformerIds.add(step.transformerId());
                    if (step.status() == ValueTransformer.Status.WARNING) warnings.add(step.code());
                    if (step.status() == ValueTransformer.Status.FAILURE) errors.add(step.code());
                });
                transformed = transformedResult.value();
            }
            if (errors.isEmpty()) {
                for (MappingPlan.Step step : mapping.validations()) {
                    if ((raw == null || raw.isBlank()) && !step.id().equals("core:required")) continue;
                    Validator<?> validator = validators.get(step.id());
                    if (validator == null || !validator.version().equals(step.version()))
                        throw new EngineException("UNKNOWN_VALIDATOR", "mapping plan references an unavailable validator");
                    ValidationResultHolder validated = validate(validator, transformed,
                            new Validator.ValidationContext(target, request.targetSchema().locale(), raw, step.options()));
                    counters.validationsExecuted++;
                    validatorIds.add(validated.result.validatorId());
                    if (validated.result.status() == Validator.Status.WARNING) warnings.add(validated.result.code());
                    if (validated.result.status() == Validator.Status.FAILURE) errors.add(validated.result.code());
                }
            }
            for (String code : warnings) counters.issue(false, code, row, mapping,
                    transformerIds.isEmpty() ? "" : transformerIds.getLast(),
                    validatorIds.isEmpty() ? "" : validatorIds.getLast(), raw);
            for (String code : errors) counters.issue(true, code, row, mapping,
                    transformerIds.isEmpty() ? "" : transformerIds.getLast(),
                    validatorIds.isEmpty() ? "" : validatorIds.getLast(), raw);
            rowWarnings.addAll(warnings); rowErrors.addAll(errors);
            RowExecutionResult.Status fieldStatus = !errors.isEmpty() ? RowExecutionResult.Status.INVALID
                    : !warnings.isEmpty() ? RowExecutionResult.Status.VALID_WITH_WARNINGS : RowExecutionResult.Status.VALID;
            fields.add(new RowExecutionResult.FieldResult(mapping.sourceColumnId(), mapping.targetFieldId(), fieldStatus,
                    transformerIds, validatorIds, warnings, errors, protect(raw)));
            if (counters.limitReached()) break;
        }
        RowExecutionResult.Status status = !rowErrors.isEmpty()
                ? request.dryRunOptions().errorPolicy() == DryRunOptions.ErrorPolicy.SKIP_ROW
                    ? RowExecutionResult.Status.SKIPPED : RowExecutionResult.Status.INVALID
                : !rowWarnings.isEmpty() ? RowExecutionResult.Status.VALID_WITH_WARNINGS : RowExecutionResult.Status.VALID;
        return new RowExecutionResult(row.recordNumber(), row.physicalLine(), status, fields, rowWarnings, rowErrors);
    }

    private static String protect(String raw) { return "<redacted:length=" + (raw == null ? 0 : raw.length()) + ">"; }

    @SuppressWarnings("unchecked")
    private static <T> ValidationResultHolder validate(Validator<T> validator, Object value,
            Validator.ValidationContext context) {
        if (value != null && validator.valueType() != Object.class && !validator.valueType().isInstance(value))
            throw new EngineException("VALIDATOR_TYPE_MISMATCH", "mapping plan has incompatible validator type");
        return new ValidationResultHolder(validator.validate((T) value, context));
    }

    private record ValidationResultHolder(Validator.ValidationResult result) {}

    private static final class DryRunCounters {
        private final DryRunOptions options;
        private final Map<String, Long> errorCodes = new LinkedHashMap<>();
        private final Map<String, Long> warningCodes = new LinkedHashMap<>();
        private final Map<String, Long> fieldErrors = new LinkedHashMap<>();
        private final List<DryRunResult.RowIssue> issueSamples = new ArrayList<>();
        private long rowsProcessed;
        private long rowsValid;
        private long rowsValidWithWarnings;
        private long rowsInvalid;
        private long rowsSkipped;
        private long cellsProcessed;
        private long nonEmptyCells;
        private long charactersObserved;
        private long transformationsApplied;
        private long validationsExecuted;
        private long manualReviewRequiredCount;
        private long totalErrorCount;
        private long totalWarningCount;
        private boolean terminated;
        private String terminationCode = "";

        private DryRunCounters(DryRunOptions options) { this.options = options; }

        private void issue(boolean error, String code, Dataset.Row row,
                MappingPlan.FieldMapping mapping, String transformerId,
                String validatorId, String raw) {
            if (error) {
                totalErrorCount++;
                mergeBounded(errorCodes, code);
                mergeBounded(fieldErrors, mapping.targetFieldId());
            } else {
                totalWarningCount++;
                mergeBounded(warningCodes, code);
            }
            if (issueSamples.size() < options.maxIssueSamples()) {
                issueSamples.add(new DryRunResult.RowIssue(row.recordNumber(), row.physicalLine(),
                        mapping.sourceColumnId(), mapping.targetFieldId(), transformerId,
                        validatorId, code, issueReason(code), protect(raw)));
            }
            if (error && totalErrorCount >= options.maxErrors()) {
                terminated = true;
                terminationCode = "MAX_ERRORS_REACHED";
            }
        }

        private void mergeBounded(Map<String, Long> target, String code) {
            if (target.containsKey(code)) target.merge(code, 1L, Long::sum);
            else if (target.size() < options.maxDistinctIssueCodes()) target.put(code, 1L);
        }

        private boolean limitReached() { return terminated; }

        private void acceptRow(RowExecutionResult result) {
            switch (result.status()) {
                case VALID -> rowsValid++;
                case VALID_WITH_WARNINGS -> { rowsValidWithWarnings++; manualReviewRequiredCount++; }
                case INVALID -> { rowsInvalid++; manualReviewRequiredCount++; }
                case SKIPPED -> { rowsSkipped++; manualReviewRequiredCount++; }
            }
            if (!result.errorCodes().isEmpty() && options.errorPolicy() == DryRunOptions.ErrorPolicy.FAIL_FAST) {
                terminated = true;
                terminationCode = "FAIL_FAST_DATA_ERROR";
            }
        }

        private DryRunResult result(DryRunRequest request, long durationMillis) {
            MappingPlan plan = request.plan();
            return new DryRunResult("1.0", ENGINE_VERSION, plan.planId(), plan.sourceId(),
                    plan.sourceFingerprint(), plan.schemaId(), plan.schemaVersion(),
                    plan.schemaFingerprint(), plan.configurationFingerprint(), rowsProcessed,
                    rowsValid, rowsValidWithWarnings, rowsInvalid, rowsSkipped, cellsProcessed,
                    nonEmptyCells, charactersObserved, plan.mappings().size(),
                    transformationsApplied, validationsExecuted, manualReviewRequiredCount,
                    totalErrorCount, totalWarningCount, fieldErrors, errorCodes, warningCodes, issueSamples,
                    terminated, terminationCode, durationMillis);
        }

        private static String issueReason(String code) {
            return switch (code) {
                case "REQUIRED_VALUE_MISSING" -> "required target field received an empty value";
                case "INVALID_CPF_FORMAT" -> "value is not in an accepted CPF representation";
                case "INVALID_CPF_CHECKSUM" -> "CPF modulus-11 checksum is invalid";
                case "INVALID_PHONE_FORMAT" -> "value is not in an accepted Brazilian phone representation";
                case "INVALID_CEP_FORMAT" -> "value is not in an accepted CEP representation";
                case "AMBIGUOUS_DATE" -> "date has more than one plausible interpretation";
                case "INVALID_DATE" -> "value does not match an explicitly accepted date format";
                case "AMBIGUOUS_DECIMAL" -> "number separators require an explicit locale";
                case "INVALID_DECIMAL", "INVALID_INTEGER", "INVALID_LONG" ->
                    "value cannot be converted to the requested numeric type";
                case "REGEX_MISMATCH" -> "value does not match the configured regular expression";
                case "LENGTH_OUT_OF_RANGE" -> "value length is outside the configured range";
                case "VALUE_NOT_IN_ENUM" -> "value is not in the configured enumeration";
                case "NUMERIC_OUT_OF_RANGE" -> "numeric value is outside the configured range";
                case "DATE_OUT_OF_RANGE" -> "date is outside the configured range";
                default -> "configured transformation or validation reported " + code;
            };
        }
    }

    private AnalysisResult score(AnalysisRequest request, DataReader.SourceStructure structure,
            long rows, List<ColumnProfile> profiles, List<String> warnings,
            String sourceFingerprint, String schemaFingerprint, String configFingerprint) {
        var allCandidates = new LinkedHashMap<String, List<MappingCandidate>>();
        var allPruned = new LinkedHashMap<String, List<PrunedCandidate>>();
        var decisions = new LinkedHashMap<String, MappingDecision>();
        var unmatched = new ArrayList<String>();
        Map<String, TargetField> targets = new HashMap<>();
        request.targetSchema().fields().forEach(field -> targets.put(field.id(), field));

        for (ColumnProfile profile : profiles) {
            ColumnFeatures features = featureExtractor.extract(profile);
            var candidates = new ArrayList<MappingCandidate>();
            CandidateGeneration generation = candidates(features, request.targetSchema().fields());
            if (generation.totalPruned() > generation.pruned().size()) {
                warnings.add("candidate pruning for source " + profile.column().id()
                        + " retained " + generation.pruned().size() + " of "
                        + generation.totalPruned() + " per-field explanations");
            }
            for (TargetField target : generation.retained()) candidates.add(candidate(features, target));
            candidates.sort(Comparator.comparing(MappingCandidate::eligible).reversed()
                    .thenComparing(Comparator.comparingDouble(MappingCandidate::score).reversed())
                    .thenComparing(MappingCandidate::targetFieldId));
            List<MappingCandidate> eligible = candidates.stream().filter(MappingCandidate::eligible).toList();
            Double margin = eligible.size() < 2 ? null : eligible.get(0).score() - eligible.get(1).score();
            MappingDecision decision = decide(profile.column().id(), eligible, margin, List.of());
            decisions.put(profile.column().id(), decision);
            if (decision.targetFieldId() == null) unmatched.add(profile.column().id());
            allCandidates.put(profile.column().id(), List.copyOf(candidates.subList(0,
                    Math.min(config.limits().maxCandidates(), candidates.size()))));
            allPruned.put(profile.column().id(), generation.pruned());
        }

        var conflicts = detectConflicts(decisions, targets);
        if (!conflicts.isEmpty()) {
            for (String conflict : conflicts) {
                String targetId = conflict.substring(conflict.indexOf(':') + 1).strip();
                decisions.replaceAll((sourceId, decision) -> targetId.equals(decision.targetFieldId())
                        ? decide(sourceId, allCandidates.get(sourceId).stream().filter(MappingCandidate::eligible).toList(),
                                decision.margin(), List.of("exclusive target collision"))
                        : decision);
            }
        }

        return new AnalysisResult("1.2", ENGINE_VERSION, "UNCALIBRATED",
                request.source().id(), sourceFingerprint, request.targetSchema().id(),
                request.targetSchema().version(), schemaFingerprint, config.version(), configFingerprint,
                structure, rows, profiles, allCandidates, allPruned, decisions, unmatched, conflicts,
                limit(warnings, config.limits().maxWarnings()), limit(List.of(), config.limits().maxErrors()));
    }

    private CandidateGeneration candidates(ColumnFeatures source, List<TargetField> targets) {
        if (targets.size() <= config.candidatePruningThreshold() || source.compactName().isEmpty()) {
            return new CandidateGeneration(targets, List.of(), 0);
        }
        var potentials = new ArrayList<PotentialTarget>();
        var pruned = new ArrayList<PrunedCandidate>();
        for (TargetField target : targets) {
            var names = new ArrayList<String>();
            names.add(target.displayName());
            names.add(target.id());
            names.addAll(target.aliases());
            boolean exactAlias = names.stream().map(normalizer::normalize)
                    .anyMatch(name -> name.compact().equals(source.compactName()));
            double tokenOverlap = names.stream().map(normalizer::normalize)
                    .mapToDouble(name -> jaccard.compare(source.normalizedName(), name.comparable()))
                    .max().orElse(0);
            double semantic = target.semanticTypes().stream()
                    .map(type -> source.semanticEvidence().get(type.id())).filter(Objects::nonNull)
                    .mapToDouble(SemanticDetector.SemanticEvidence::semanticConfidence).max().orElse(0);
            boolean physical = target.physicalType() != PhysicalType.TEXT
                    && target.physicalType() == source.inferredType();
            boolean strongContradiction = source.semanticEvidence().values().stream()
                    .anyMatch(item -> item.strongIdentity() && !target.semanticTypes().contains(item.type()));
            double signal = .65 * tokenOverlap + .30 * semantic + (physical ? .05 : 0);
            if (exactAlias || tokenOverlap > 0 || semantic > 0 || physical) {
                potentials.add(new PotentialTarget(target, exactAlias, signal));
            } else {
                pruned.add(new PrunedCandidate(target.id(), strongContradiction
                        ? "strong semantic incompatibility and no lexical/type support"
                        : "no alias, token, semantic or specific physical-type support"));
            }
        }
        potentials.sort(Comparator.comparing(PotentialTarget::exactAlias).reversed()
                .thenComparing(Comparator.comparingDouble(PotentialTarget::signal).reversed())
                .thenComparing(item -> item.target().id()));
        var retained = new ArrayList<TargetField>();
        int nonExactRetained = 0;
        for (PotentialTarget potential : potentials) {
            if (potential.exactAlias() || nonExactRetained < config.candidateShortlistSize()) {
                retained.add(potential.target());
                if (!potential.exactAlias()) nonExactRetained++;
            } else {
                pruned.add(new PrunedCandidate(potential.target().id(),
                        "candidate fell below configured explainable shortlist"));
            }
        }
        pruned.sort(Comparator.comparing(PrunedCandidate::targetFieldId));
        int totalPruned = pruned.size();
        int explanationLimit = config.limits().maxPrunedCandidateExplanations();
        return new CandidateGeneration(List.copyOf(retained), List.copyOf(pruned.subList(0,
                Math.min(explanationLimit, totalPruned))), totalPruned);
    }

    private record PotentialTarget(TargetField target, boolean exactAlias, double signal) {}
    private record CandidateGeneration(List<TargetField> retained, List<PrunedCandidate> pruned,
                                       int totalPruned) {}

    private MappingCandidate candidate(ColumnFeatures features, TargetField target) {
        var components = new ArrayList<ScoreComponent>();
        var contradictions = new ArrayList<String>();
        components.add(lexical(features, target));
        components.add(physical(features, target));

        SemanticDetector.SemanticEvidence accepted = target.semanticTypes().stream()
                .map(type -> features.semanticEvidence().get(type.id())).filter(Objects::nonNull)
                .max(Comparator.comparingDouble(SemanticDetector.SemanticEvidence::validityScore)).orElse(null);
        if (target.semanticTypes().isEmpty()) {
            components.add(unavailable("semantic", "target declares no semantic type"));
            components.add(unavailable("pattern", "target declares no semantic type"));
        } else if (accepted == null) {
            components.add(unavailable("semantic", "no configured detector for accepted semantic types"));
            components.add(unavailable("pattern", "no configured detector for accepted semantic types"));
        } else {
            components.add(available("semantic", accepted.validityScore(), accepted.reliability(),
                    accepted.explanation(), accepted.ambiguous() > 0 ? List.of("ambiguous values reduce reliability") : List.of()));
            components.add(available("pattern", accepted.shapeScore(), accepted.reliability(),
                    "shape matches " + accepted.shapeMatches() + "/" + accepted.observed(), List.of()));
        }
        components.add(unavailable("distribution", "target distribution is not provided"));
        components.add(unavailable("history", "mapping history is not implemented"));

        for (var evidence : features.semanticEvidence().values()) {
            if (evidence.strongIdentity() && !target.semanticTypes().contains(evidence.type())) {
                contradictions.add("strong semantic evidence " + evidence.type().id() + " is incompatible with target");
            }
        }

        double numerator = components.stream().mapToDouble(ScoreComponent::contribution).sum();
        double effectiveWeight = components.stream().filter(ScoreComponent::available)
                .mapToDouble(c -> c.weight() * c.reliability()).sum();
        double totalWeight = config.weights().values().stream().mapToDouble(Double::doubleValue).sum();
        double score = effectiveWeight == 0 ? 0 : numerator / effectiveWeight;
        double coverage = totalWeight == 0 ? 0 : effectiveWeight / totalWeight;
        double confidence = contradictions.isEmpty() ? score * coverage : 0;
        return new MappingCandidate(target.id(), target.displayName(), finite(score), finite(coverage),
                finite(confidence), contradictions.isEmpty(), components, contradictions);
    }

    private ScoreComponent lexical(ColumnFeatures features, TargetField target) {
        if (features.compactName().isEmpty()) return unavailable("lexical", "source header is empty");
        var names = new ArrayList<String>(); names.add(target.displayName()); names.add(target.id()); names.addAll(target.aliases());
        if (config.lexicalStrategy() == EngineConfig.LexicalStrategy.BASELINE_0_1) {
            double bestDice = 0;
            double bestLevenshtein = 0;
            for (String name : names) {
                var normalized = normalizer.normalize(name);
                bestDice = Math.max(bestDice, dice.compare(features.normalizedName(), normalized.comparable()));
                bestLevenshtein = Math.max(bestLevenshtein,
                        levenshtein.compare(features.compactName(), normalized.compact()));
            }
            return available("lexical", (bestDice + bestLevenshtein) / 2.0, 1.0,
                    "baseline 0.1: max Dice=" + round(bestDice)
                            + ", max normalized Levenshtein=" + round(bestLevenshtein),
                    List.of("baseline retained only for reproducible evaluation"));
        }
        LexicalEvidence best = null;
        for (String name : names) {
            var normalized = normalizer.normalize(name);
            double diceValue = dice.compare(features.normalizedName(), normalized.comparable());
            double jaccardValue = jaccard.compare(features.normalizedName(), normalized.comparable());
            double levenshteinValue = levenshtein.compare(features.compactName(), normalized.compact());
            double jaroValue = jaro.compare(features.compactName(), normalized.compact());
            double winklerValue = jaroWinkler.compare(features.compactName(), normalized.compact());
            double ngramValue = ngram.compare(features.compactName(), normalized.compact());
            double cosineValue = cosine.compare(features.compactName(), normalized.compact());
            double tokenScore = (2 * diceValue + jaccardValue) / 3.0;
            double editScore = (levenshteinValue + jaroValue + winklerValue) / 3.0;
            double gramScore = (ngramValue + cosineValue) / 2.0;
            double combined = .40 * tokenScore + .35 * editScore + .25 * gramScore;
            var current = new LexicalEvidence(combined, diceValue, jaccardValue,
                    levenshteinValue, jaroValue, winklerValue, ngramValue, cosineValue);
            if (best == null || current.score() > best.score()) best = current;
        }
        Objects.requireNonNull(best);
        return available("lexical", best.score(), 1.0,
                "winning representation: Dice=" + round(best.dice())
                        + ", Jaccard=" + round(best.jaccard())
                        + ", normalized Levenshtein=" + round(best.levenshtein())
                        + ", Jaro=" + round(best.jaro())
                        + ", Jaro-Winkler=" + round(best.jaroWinkler())
                        + ", trigram Dice=" + round(best.ngram())
                        + ", trigram cosine=" + round(best.cosine()),
                List.of("correlated metrics are grouped into token, edit and character-gram subscores",
                        "only one target representation contributes; independent per-metric maxima are not combined"));
    }

    private record LexicalEvidence(double score, double dice, double jaccard,
                                   double levenshtein, double jaro, double jaroWinkler,
                                   double ngram, double cosine) {}

    private ScoreComponent physical(ColumnFeatures features, TargetField target) {
        long observed = features.observedValues();
        if (observed == 0) return unavailable("physicalType", "column has no nonblank values");
        double value = target.physicalType() == PhysicalType.TEXT || target.physicalType() == features.inferredType()
                ? 1.0 : features.inferredType() == PhysicalType.MIXED ? .5 : 0;
        double reliability = Math.min(1.0, (double) observed / config.minimumEvidenceValues());
        return available("physicalType", value, reliability,
                "inferred=" + features.inferredType() + ", expected=" + target.physicalType(), List.of());
    }

    private ScoreComponent available(String id, double value, double reliability, String evidence,
                                     List<String> limitations) {
        validateUnit(value, id); validateUnit(reliability, id + " reliability");
        double weight = config.weight(id);
        return new ScoreComponent(id, true, value, weight, reliability, weight * reliability * value,
                evidence, null, limitations);
    }

    private ScoreComponent unavailable(String id, String reason) {
        return new ScoreComponent(id, false, null, config.weight(id), 0, 0, null, reason, List.of());
    }

    private MappingDecision decide(String sourceId, List<MappingCandidate> candidates, Double margin,
                                   List<String> externalBlockers) {
        if (candidates.isEmpty()) return new MappingDecision(sourceId, null, DecisionStatus.NO_MATCH,
                0, 0, margin, 0, List.of("no target candidates"), List.of());
        MappingCandidate best = candidates.get(0);
        var blockers = new ArrayList<String>(externalBlockers);
        blockers.addAll(best.contradictions());
        if (margin == null) blockers.add("margin unavailable with a single candidate");
        else if (margin < config.minimumMargin()) blockers.add("candidate margin below configured minimum");
        if (best.coverage() < config.minimumCoverage()) blockers.add("evidence coverage below configured minimum");
        if (!config.autoMapEnabled()) blockers.add("AUTO_MAP is disabled by core configuration");

        DecisionStatus status;
        String target = best.targetFieldId();
        if (best.score() < config.lowThreshold() || !best.eligible()) {
            status = DecisionStatus.NO_MATCH; target = null;
        } else if (config.autoMapEnabled() && blockers.isEmpty() && best.score() >= config.autoMapThreshold()) {
            status = DecisionStatus.AUTO_MAP;
        } else if (best.score() >= config.reviewThreshold()) {
            status = DecisionStatus.REVIEW_RECOMMENDED;
        } else {
            status = DecisionStatus.LOW_CONFIDENCE;
        }
        return new MappingDecision(sourceId, target, status, best.score(), best.coverage(), margin,
                best.confidenceIndex(), List.of("highest eligible deterministic score"), blockers);
    }

    private List<String> detectConflicts(Map<String, MappingDecision> decisions, Map<String, TargetField> targets) {
        var owners = new HashMap<String, List<String>>();
        decisions.forEach((source, decision) -> {
            if (decision.targetFieldId() != null && targets.get(decision.targetFieldId()).exclusive())
                owners.computeIfAbsent(decision.targetFieldId(), ignored -> new ArrayList<>()).add(source);
        });
        var conflicts = new ArrayList<String>();
        owners.forEach((target, sources) -> { if (sources.size() > 1) conflicts.add("exclusive target collision: " + target); });
        conflicts.sort(String::compareTo); return conflicts;
    }

    private void validateStructure(DataReader.SourceStructure structure) {
        if (structure.columns().size() > config.limits().maxColumns())
            throw new EngineException("COLUMN_LIMIT", "source exceeds configured column limit");
        for (var column : structure.columns()) {
            if (column.header().length() > config.limits().maxHeaderChars())
                throw new EngineException("HEADER_LIMIT", "header exceeds configured character limit");
        }
    }

    private void checkExecution(long started) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        if (elapsed.compareTo(config.limits().maxDuration()) > 0)
            throw new EngineException("TIME_LIMIT", "analysis exceeded configured duration");
    }

    private static <T> List<T> limit(List<T> values, int maximum) {
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
    }
    private static double finite(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    private static double round(double value) { return Math.round(value * 10_000.0) / 10_000.0; }
    private static void validateUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name + " must be in [0,1]");
    }
    static String schemaFingerprint(TargetSchema schema) { return fingerprint(schemaCanonical(schema)); }
    private String configurationFingerprint(AnalysisOptions options) {
        return fingerprint(configCanonical(config) + '|'
                + new java.util.TreeMap<>(options.readerOptions()) + '|' + options.sampleSeed()
                + '|' + executionRegistryCanonical);
    }
    private static String schemaCanonical(TargetSchema schema) {
        var value = new StringBuilder().append(schema.id()).append('|').append(schema.version()).append('|')
                .append(schema.context()).append('|').append(schema.locale());
        for (TargetField field : schema.fields()) {
            value.append('|').append(field.id()).append('|').append(field.displayName()).append('|')
                    .append(field.aliases()).append('|').append(field.physicalType()).append('|')
                    .append(field.semanticTypes().stream().map(SemanticType::id).sorted().toList())
                    .append('|').append(field.required()).append('|').append(field.exclusive());
        }
        return value.toString();
    }
    private static String configCanonical(EngineConfig config) {
        return config.version() + '|' + config.limits() + '|'
                + new java.util.TreeMap<>(config.weights()) + '|' + config.minimumEvidenceValues() + '|'
                + config.autoMapThreshold() + '|' + config.reviewThreshold() + '|' + config.lowThreshold() + '|'
                + config.minimumMargin() + '|' + config.minimumCoverage() + '|' + config.autoMapEnabled() + '|'
                + config.candidatePruningThreshold() + '|' + config.candidateShortlistSize() + '|'
                + config.lexicalStrategy();
    }
    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Builder for immutable engine composition. */
    public static final class Builder {
        private final List<DataReader> readers = new ArrayList<>();
        private final List<SemanticDetector> detectors = new ArrayList<>();
        private final List<ValueTransformer<?, ?>> transformers = new ArrayList<>(BuiltInTransformers.defaults());
        private final List<Validator<?>> validators = new ArrayList<>(BuiltInValidators.defaults());
        private EngineConfig config = EngineConfig.defaults();
        private HeaderNormalizer normalizer = new HeaderNormalizer(Map.of());
        /** Replaces the ordered format reader set. */
        public Builder readers(List<DataReader> value) { readers.clear(); readers.addAll(value); return this; }
        /** Replaces semantic detectors; duplicate semantic type IDs are rejected. */
        public Builder semanticDetectors(List<SemanticDetector> value) { detectors.clear(); detectors.addAll(value); return this; }
        /** Sets immutable limits, scoring weights and confidence policy. */
        public Builder configuration(EngineConfig value) { config = Objects.requireNonNull(value); return this; }
        /** Sets the non-destructive header normalizer used by extraction and matching. */
        public Builder normalizer(HeaderNormalizer value) { normalizer = Objects.requireNonNull(value); return this; }
        /** Replaces the transformer registry. Core transformers are present by default. */
        public Builder transformers(List<ValueTransformer<?, ?>> value) {
            transformers.clear(); transformers.addAll(Objects.requireNonNull(value)); return this;
        }
        /** Replaces the validator registry. Core validators are present by default. */
        public Builder validators(List<Validator<?>> value) {
            validators.clear(); validators.addAll(Objects.requireNonNull(value)); return this;
        }
        /** Validates composition and builds an engine for sequential reuse. */
        public MappingEngine build() { return new MappingEngine(this); }
    }

    private static final class ProfileAccumulator {
        private final DataReader.SourceColumn column;
        private final List<SemanticDetector.Accumulator> semantic;
        private final List<SemanticDetector> detectors;
        private final EngineConfig config;
        private final Random random;
        private final List<String> samples = new ArrayList<>();
        private final EnumMap<PhysicalType, Long> votes = new EnumMap<>(PhysicalType.class);
        private final HyperLogLogSketch cardinality = new HyperLogLogSketch();
        private final BoundedFrequencyTracker frequentValues;
        private final Map<String, Long> exactFrequencies = new HashMap<>();
        private final Map<String, Long> lengths = new LinkedHashMap<>();
        private final Map<String, Long> patterns = new LinkedHashMap<>();
        private final Map<String, ColumnProfile.AnomalyLocation> patternLocations = new HashMap<>();
        private final Map<String, ColumnProfile.AnomalyLocation> lengthLocations = new HashMap<>();
        private final Map<String, List<ColumnProfile.AnomalyLocation>> semanticInvalidLocations = new HashMap<>();
        private final List<ColumnProfile.AnomalyLocation> nullLocations = new ArrayList<>();
        private boolean exactFrequenciesComplete = true;
        private long numericCount;
        private double numericMean;
        private double numericM2;
        private double numericMinimum = Double.POSITIVE_INFINITY;
        private double numericMaximum = Double.NEGATIVE_INFINITY;
        private long rows, nulls, lengthTotal; private int min = Integer.MAX_VALUE, max;

        ProfileAccumulator(DataReader.SourceColumn column, List<SemanticDetector> detectors,
                           EngineConfig config, long seed) {
            this.column = column; this.detectors = detectors; this.config = config; this.random = new Random(seed);
            this.semantic = detectors.stream().map(SemanticDetector::newAccumulator).toList();
            this.frequentValues = new BoundedFrequencyTracker(config.limits().maxFrequentValues());
        }

        void accept(String raw, long recordNumber, long physicalLine) {
            rows++;
            String value = raw == null ? "" : raw;
            if (value.length() > config.limits().maxFieldChars())
                throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
            if (value.isBlank()) {
                nulls++;
                votes.merge(PhysicalType.EMPTY, 1L, Long::sum);
                if (nullLocations.size() < 3) {
                    nullLocations.add(location(recordNumber, physicalLine, 0));
                }
                return;
            }
            int length = value.length(); min = Math.min(min, length); max = Math.max(max, length); lengthTotal += length;
            PhysicalType physicalType = classify(value);
            votes.merge(physicalType, 1L, Long::sum);
            for (int i = 0; i < semantic.size(); i++) {
                semantic.get(i).accept(value);
                String type = detectors.get(i).type().id();
                List<ColumnProfile.AnomalyLocation> locations = semanticInvalidLocations
                        .computeIfAbsent(type, ignored -> new ArrayList<>());
                if (locations.size() < 3) {
                    SemanticDetector.ValueEvidence inspected = detectors.get(i).inspect(value);
                    if (inspected.available() && !inspected.valid()) {
                        locations.add(location(recordNumber, physicalLine, length));
                    }
                }
            }
            cardinality.add(value);
            frequentValues.add(value);
            trackExactFrequency(value);
            String lengthBucket = lengthBucket(length);
            lengths.merge(lengthBucket, 1L, Long::sum);
            lengthLocations.putIfAbsent(lengthBucket, location(recordNumber, physicalLine, length));
            String pattern = pattern(value);
            patterns.merge(pattern, 1L, Long::sum);
            patternLocations.putIfAbsent(pattern, location(recordNumber, physicalLine, length));
            if (physicalType == PhysicalType.INTEGER || physicalType == PhysicalType.DECIMAL) {
                acceptNumeric(value);
            }
            String protectedValue = "<redacted:length=" + length + ">";
            int capacity = config.limits().maxSamples();
            if (capacity > 0) {
                if (samples.size() < capacity) samples.add(protectedValue);
                else { long pick = Math.floorMod(random.nextLong(), rows); if (pick < capacity) samples.set((int) pick, protectedValue); }
            }
        }

        ColumnProfile finish() {
            var evidence = new LinkedHashMap<String, SemanticDetector.SemanticEvidence>();
            for (int i = 0; i < detectors.size(); i++) {
                var item = semantic.get(i).finish(config.minimumEvidenceValues()); evidence.put(item.type().id(), item);
            }
            long nonblank = rows - nulls;
            var statistics = statistics(evidence, nonblank);
            return new ColumnProfile(column, rows, nulls, nonblank == 0 ? 0 : min, max,
                    nonblank == 0 ? 0 : (double) lengthTotal / nonblank, votes, inferred(votes), evidence,
                    samples, ColumnProfile.MeasureAccuracy.EXACT,
                    "all records for counts/evidence; deterministic reservoir for protected samples",
                    statistics);
        }

        private void trackExactFrequency(String value) {
            if (!exactFrequenciesComplete) return;
            Long count = exactFrequencies.get(value);
            if (count != null) {
                exactFrequencies.put(value, count + 1);
            } else if (exactFrequencies.size() < config.limits().maxTrackedDistinctValues()) {
                exactFrequencies.put(value, 1L);
            } else {
                exactFrequenciesComplete = false;
                exactFrequencies.clear();
            }
        }

        private void acceptNumeric(String raw) {
            try {
                double value = Double.parseDouble(raw.strip().replace(',', '.'));
                if (!Double.isFinite(value)) return;
                numericCount++;
                double delta = value - numericMean;
                numericMean += delta / numericCount;
                numericM2 += delta * (value - numericMean);
                numericMinimum = Math.min(numericMinimum, value);
                numericMaximum = Math.max(numericMaximum, value);
            } catch (NumberFormatException ignored) {
                // The physical classifier is conservative; an unparseable value is not numeric evidence.
            }
        }

        private ColumnProfile.ColumnStatistics statistics(
                Map<String, SemanticDetector.SemanticEvidence> evidence, long nonblank) {
            long distinct = exactFrequenciesComplete ? exactFrequencies.size() : cardinality.estimate();
            distinct = Math.min(nonblank, distinct);
            var cardinalityMeasure = new ColumnProfile.Cardinality(distinct,
                    exactFrequenciesComplete ? ColumnProfile.MeasureAccuracy.EXACT
                            : ColumnProfile.MeasureAccuracy.ESTIMATED,
                    exactFrequenciesComplete ? "bounded exact frequency table" : "HyperLogLog p=10, 1024 registers",
                    exactFrequenciesComplete ? null : HyperLogLogSketch.EXPECTED_RELATIVE_ERROR);
            List<ColumnProfile.FrequentValue> topValues = topValues();
            Entropy entropy = entropy(nonblank, distinct);
            ColumnProfile.NumericSummary numeric = numericCount == 0 ? null : new ColumnProfile.NumericSummary(
                    numericCount, numericMean, numericM2 / numericCount,
                    Math.sqrt(numericM2 / numericCount), numericMinimum, numericMaximum,
                    ColumnProfile.MeasureAccuracy.EXACT);
            long dominantPhysical = votes.entrySet().stream().filter(entry -> entry.getKey() != PhysicalType.EMPTY)
                    .mapToLong(Map.Entry::getValue).max().orElse(0);
            double mixedRatio = nonblank == 0 ? 0 : 1.0 - (double) dominantPhysical / nonblank;
            SemanticDetector.SemanticEvidence dominant = evidence.values().stream()
                    .max(Comparator.comparingDouble(SemanticDetector.SemanticEvidence::semanticConfidence)
                            .thenComparing(item -> item.type().id())).orElse(null);
            String dominantType = dominant == null || dominant.semanticConfidence() < .5
                    ? "" : dominant.type().id();
            SemanticDetector.SemanticEvidence publishedDominant = dominantType.isEmpty() ? null : dominant;
            double validRatio = publishedDominant == null ? 0 : publishedDominant.validityScore();
            return new ColumnProfile.ColumnStatistics(cardinalityMeasure,
                    nonblank == 0 ? 0 : (double) distinct / nonblank, topValues,
                    entropy.value(), entropy.accuracy(), entropy.error(), numeric,
                    new LinkedHashMap<>(lengths), new LinkedHashMap<>(patterns), mixedRatio,
                    rows == 0 ? 0 : (double) nulls / rows, dominantType,
                    publishedDominant == null ? 0 : publishedDominant.semanticConfidence(), validRatio,
                    publishedDominant == null ? 0 : 1.0 - validRatio,
                    anomalies(evidence, dominant, nonblank, distinct, mixedRatio));
        }

        private List<ColumnProfile.FrequentValue> topValues() {
            List<BoundedFrequencyTracker.Entry> entries;
            ColumnProfile.MeasureAccuracy accuracy;
            if (exactFrequenciesComplete) {
                entries = exactFrequencies.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .limit(config.limits().maxFrequentValues())
                        .map(entry -> new BoundedFrequencyTracker.Entry(entry.getKey(), entry.getValue(), 0))
                        .toList();
                accuracy = ColumnProfile.MeasureAccuracy.EXACT;
            } else {
                entries = frequentValues.entries();
                accuracy = ColumnProfile.MeasureAccuracy.ESTIMATED;
            }
            var result = new ArrayList<ColumnProfile.FrequentValue>();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                result.add(new ColumnProfile.FrequentValue(i + 1,
                        "<redacted:length=" + entry.value().length() + ">",
                        entry.count(), entry.error(), accuracy));
            }
            return List.copyOf(result);
        }

        private Entropy entropy(long nonblank, long distinct) {
            if (nonblank == 0) return new Entropy(null, ColumnProfile.MeasureAccuracy.UNAVAILABLE, null);
            if (exactFrequenciesComplete) {
                double value = entropyOf(exactFrequencies.values(), nonblank);
                return new Entropy(value, ColumnProfile.MeasureAccuracy.EXACT, 0.0);
            }
            List<BoundedFrequencyTracker.Entry> entries = frequentValues.entries();
            var lowerCounts = entries.stream().map(entry -> Math.max(0L, entry.count() - entry.error())).toList();
            long accounted = lowerCounts.stream().mapToLong(Long::longValue).sum();
            long residual = Math.max(0, nonblank - accounted);
            double lower = entropyOf(lowerCounts, nonblank) + entropyTerm(residual, nonblank);
            long remainingCategories = Math.max(1, distinct - lowerCounts.stream().filter(value -> value > 0).count());
            long quotient = residual / remainingCategories;
            long remainder = residual % remainingCategories;
            double upper = entropyOf(lowerCounts, nonblank)
                    + remainder * entropyTerm(quotient + 1, nonblank)
                    + (remainingCategories - remainder) * entropyTerm(quotient, nonblank);
            if (upper < lower) upper = lower;
            return new Entropy((lower + upper) / 2.0, ColumnProfile.MeasureAccuracy.ESTIMATED,
                    (upper - lower) / 2.0);
        }

        private List<ColumnProfile.ColumnAnomaly> anomalies(
                Map<String, SemanticDetector.SemanticEvidence> evidence,
                SemanticDetector.SemanticEvidence dominant, long nonblank,
                long distinct, double mixedRatio) {
            var result = new ArrayList<ColumnProfile.ColumnAnomaly>();
            if (nulls > 0 && nonblank > 0) {
                result.add(anomaly("NULL_PRESENT", nulls, rows, ColumnProfile.MeasureAccuracy.EXACT,
                        "blank values are present; target requiredness is evaluated separately", nullLocations));
            }
            long dominantCount = votes.entrySet().stream().filter(entry -> entry.getKey() != PhysicalType.EMPTY)
                    .mapToLong(Map.Entry::getValue).max().orElse(0);
            long physicalMinority = nonblank - dominantCount;
            if (physicalMinority > 0) {
                String code = (double) dominantCount / nonblank >= .8
                        ? "PHYSICAL_TYPE_OUTLIER" : "MIXED_PHYSICAL_TYPES";
                result.add(anomaly(code, physicalMinority, nonblank,
                        ColumnProfile.MeasureAccuracy.EXACT,
                        "minority values disagree with the dominant physical type", List.of()));
            }
            addRare(result, "RARE_FORMAT", patterns, patternLocations, nonblank);
            addRare(result, "RARE_LENGTH", lengths, lengthLocations, nonblank);
            if (dominant != null && dominant.semanticConfidence() >= .5
                    && dominant.validMatches() < dominant.observed()) {
                result.add(anomaly("SEMANTIC_INVALID", dominant.observed() - dominant.validMatches(),
                        dominant.observed(), ColumnProfile.MeasureAccuracy.EXACT,
                        "values fail dominant semantic type " + dominant.type().id(),
                        semanticInvalidLocations.getOrDefault(dominant.type().id(), List.of())));
            }
            if (dominant != null && dominant.strongIdentity() && distinct < nonblank) {
                result.add(anomaly("DUPLICATE_IDENTITY", nonblank - distinct, nonblank,
                        exactFrequenciesComplete ? ColumnProfile.MeasureAccuracy.EXACT
                                : ColumnProfile.MeasureAccuracy.ESTIMATED,
                        "duplicate values occur in a column with strong identity evidence", List.of()));
            }
            if (mixedRatio > .2 && dominant != null && dominant.semanticConfidence() > 0) {
                result.add(anomaly("SEMANTIC_MIXTURE", Math.round(mixedRatio * nonblank), nonblank,
                        ColumnProfile.MeasureAccuracy.EXACT,
                        "physical mixture limits semantic confidence", List.of()));
            }
            return List.copyOf(result.subList(0, Math.min(result.size(), config.limits().maxAnomalies())));
        }

        private void addRare(List<ColumnProfile.ColumnAnomaly> result, String code,
                Map<String, Long> distribution,
                Map<String, ColumnProfile.AnomalyLocation> locations, long nonblank) {
            if (nonblank < 20 || distribution.size() < 2) return;
            long dominant = distribution.values().stream().mapToLong(Long::longValue).max().orElse(0);
            if ((double) dominant / nonblank < .8) return;
            distribution.entrySet().stream().filter(entry -> (double) entry.getValue() / nonblank <= .05)
                    .sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(anomaly(code,
                            entry.getValue(), nonblank, ColumnProfile.MeasureAccuracy.EXACT,
                            "rare bucket " + entry.getKey() + " differs from the dominant distribution",
                            List.of(locations.get(entry.getKey())))));
        }

        private static ColumnProfile.ColumnAnomaly anomaly(String code, long count, long denominator,
                ColumnProfile.MeasureAccuracy accuracy, String explanation,
                List<ColumnProfile.AnomalyLocation> locations) {
            return new ColumnProfile.ColumnAnomaly(code, count,
                    denominator == 0 ? 0 : (double) count / denominator,
                    accuracy, explanation, locations);
        }

        private static ColumnProfile.AnomalyLocation location(long record, long line, int length) {
            return new ColumnProfile.AnomalyLocation(record, line, "<redacted:length=" + length + ">");
        }

        private static double entropyOf(Iterable<Long> counts, long total) {
            double entropy = 0;
            for (long count : counts) entropy += entropyTerm(count, total);
            return entropy;
        }

        private static double entropyTerm(long count, long total) {
            if (count <= 0 || total <= 0) return 0;
            double probability = (double) count / total;
            return -probability * (Math.log(probability) / Math.log(2));
        }

        private static String lengthBucket(int length) {
            if (length <= 4) return "1-4";
            if (length <= 8) return "5-8";
            if (length <= 12) return "9-12";
            if (length <= 20) return "13-20";
            if (length <= 40) return "21-40";
            if (length <= 80) return "41-80";
            return "81+";
        }

        private static String pattern(String raw) {
            String value = raw.strip();
            if (value.matches("[^\\s@]+@[^\\s@]+")) return "EMAIL_LIKE";
            if (value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}")) return "DATE_LIKE";
            if (value.matches("[-+]?\\d+")) return "INTEGER_LIKE";
            if (value.matches("[-+]?\\d+[.,]\\d+")) return "DECIMAL_LIKE";
            if (value.matches("[\\p{L}\\d_-]+")) return "ALPHANUMERIC";
            if (value.matches("[\\p{L}\\s.'-]+")) return "TEXT_WORDS";
            return "OTHER";
        }

        private record Entropy(Double value, ColumnProfile.MeasureAccuracy accuracy, Double error) {}

        private static PhysicalType classify(String raw) {
            String value = raw.strip();
            if (value.matches("(?i:true|false|yes|no|sim|nao|não)")) return PhysicalType.BOOLEAN;
            if (value.matches("[-+]?\\d+") && !value.matches("[-+]?0\\d+")) return PhysicalType.INTEGER;
            if (value.matches("[-+]?(?:\\d+[.,]\\d+)") ) return PhysicalType.DECIMAL;
            if (value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}")) return PhysicalType.DATE;
            return PhysicalType.TEXT;
        }

        private static PhysicalType inferred(EnumMap<PhysicalType, Long> votes) {
            long total = votes.entrySet().stream().filter(e -> e.getKey() != PhysicalType.EMPTY).mapToLong(Map.Entry::getValue).sum();
            if (total == 0) return PhysicalType.EMPTY;
            var best = votes.entrySet().stream().filter(e -> e.getKey() != PhysicalType.EMPTY)
                    .max(Map.Entry.comparingByValue()).orElseThrow();
            return (double) best.getValue() / total >= .8 ? best.getKey() : PhysicalType.MIXED;
        }
    }
}
