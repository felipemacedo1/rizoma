package io.github.rizoma.core;

import io.github.rizoma.core.AnalysisResult.DecisionStatus;
import io.github.rizoma.core.AnalysisResult.MappingCandidate;
import io.github.rizoma.core.AnalysisResult.MappingDecision;
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
 * Stateless orchestrator for explainable analysis. Instances are safe for
 * sequential reuse. Concurrent use is not guaranteed because supplied readers
 * and detectors may have their own thread-safety constraints.
 */
public final class MappingEngine {
    public static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private final List<DataReader> readers;
    private final List<SemanticDetector> detectors;
    private final EngineConfig config;
    private final HeaderNormalizer normalizer;
    private final ColumnFeatureExtractor featureExtractor;
    private final SimilarityMetric dice = new DiceSimilarity();
    private final SimilarityMetric levenshtein = new LevenshteinSimilarity();

    private MappingEngine(Builder builder) {
        readers = List.copyOf(builder.readers);
        detectors = List.copyOf(builder.detectors);
        config = builder.config;
        normalizer = builder.normalizer;
        featureExtractor = new ColumnFeatureExtractor(normalizer);
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
                    for (int i = 0; i < accumulators.size(); i++) accumulators.get(i).accept(row.values().get(i));
                }
                warnings.addAll(dataset.warnings());
            }

            var profiles = accumulators.stream().map(ProfileAccumulator::finish).toList();
            String fingerprintAfterRead = request.source().sha256();
            if (!sourceFingerprint.equals(fingerprintAfterRead)) {
                throw new EngineException("SOURCE_CHANGED", "source content changed during analysis");
            }
            String schemaFingerprint = fingerprint(schemaCanonical(request.targetSchema()));
            String configFingerprint = fingerprint(configCanonical(config) + '|'
                    + new java.util.TreeMap<>(request.options().readerOptions()) + '|'
                    + request.options().sampleSeed());
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

    private AnalysisResult score(AnalysisRequest request, DataReader.SourceStructure structure,
            long rows, List<ColumnProfile> profiles, List<String> warnings,
            String sourceFingerprint, String schemaFingerprint, String configFingerprint) {
        var allCandidates = new LinkedHashMap<String, List<MappingCandidate>>();
        var decisions = new LinkedHashMap<String, MappingDecision>();
        var unmatched = new ArrayList<String>();
        Map<String, TargetField> targets = new HashMap<>();
        request.targetSchema().fields().forEach(field -> targets.put(field.id(), field));

        for (ColumnProfile profile : profiles) {
            ColumnFeatures features = featureExtractor.extract(profile);
            var candidates = new ArrayList<MappingCandidate>();
            for (TargetField target : request.targetSchema().fields()) candidates.add(candidate(features, target));
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

        return new AnalysisResult("1.1", ENGINE_VERSION, "UNCALIBRATED",
                request.source().id(), sourceFingerprint, request.targetSchema().id(),
                request.targetSchema().version(), schemaFingerprint, config.version(), configFingerprint,
                structure, rows, profiles, allCandidates, decisions, unmatched, conflicts,
                limit(warnings, config.limits().maxWarnings()), limit(List.of(), config.limits().maxErrors()));
    }

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
        double bestDice = 0, bestLevenshtein = 0;
        for (String name : names) {
            var normalized = normalizer.normalize(name);
            bestDice = Math.max(bestDice, dice.compare(features.normalizedName(), normalized.comparable()));
            bestLevenshtein = Math.max(bestLevenshtein, levenshtein.compare(features.compactName(), normalized.compact()));
        }
        double value = (bestDice + bestLevenshtein) / 2.0;
        return available("lexical", value, 1.0,
                "max Dice=" + round(bestDice) + ", max normalized Levenshtein=" + round(bestLevenshtein),
                List.of("Dice and Levenshtein form one correlated lexical subscore"));
    }

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
                + config.minimumMargin() + '|' + config.minimumCoverage() + '|' + config.autoMapEnabled();
    }
    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Builder for immutable engine composition. */
    public static final class Builder {
        private final List<DataReader> readers = new ArrayList<>();
        private final List<SemanticDetector> detectors = new ArrayList<>();
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
        private long rows, nulls, lengthTotal; private int min = Integer.MAX_VALUE, max;

        ProfileAccumulator(DataReader.SourceColumn column, List<SemanticDetector> detectors,
                           EngineConfig config, long seed) {
            this.column = column; this.detectors = detectors; this.config = config; this.random = new Random(seed);
            this.semantic = detectors.stream().map(SemanticDetector::newAccumulator).toList();
        }

        void accept(String raw) {
            rows++;
            String value = raw == null ? "" : raw;
            if (value.length() > config.limits().maxFieldChars())
                throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
            if (value.isBlank()) { nulls++; votes.merge(PhysicalType.EMPTY, 1L, Long::sum); return; }
            int length = value.length(); min = Math.min(min, length); max = Math.max(max, length); lengthTotal += length;
            votes.merge(classify(value), 1L, Long::sum);
            for (var accumulator : semantic) accumulator.accept(value);
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
            return new ColumnProfile(column, rows, nulls, nonblank == 0 ? 0 : min, max,
                    nonblank == 0 ? 0 : (double) lengthTotal / nonblank, votes, inferred(votes), evidence,
                    samples, ColumnProfile.MeasureAccuracy.EXACT,
                    "all records for counts/evidence; deterministic reservoir for protected samples");
        }

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
