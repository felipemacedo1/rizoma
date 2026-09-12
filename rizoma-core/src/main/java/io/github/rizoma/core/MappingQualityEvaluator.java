package io.github.rizoma.core;

import io.github.rizoma.core.AnalysisResult.DecisionStatus;
import io.github.rizoma.core.AnalysisResult.MappingCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reproducible quality metrics over labeled, synthetic analysis results. */
public final class MappingQualityEvaluator {
    /** Evaluates ranking, simulated auto-map, abstention and semantic confusion. */
    public EvaluationReport evaluate(List<EvaluatedDataset> datasets, EngineConfig policy) {
        Objects.requireNonNull(datasets, "datasets");
        Objects.requireNonNull(policy, "policy");
        long columns = 0, mappable = 0, top1 = 0, top3 = 0;
        long simulatedAutoMaps = 0, correctAutoMaps = 0, falsePositives = 0;
        long abstentionCases = 0, correctAbstentions = 0, noMatchCases = 0, correctNoMatches = 0;
        long reviews = 0;
        var confusion = new LinkedHashMap<String, Map<String, Long>>();
        var failures = new ArrayList<Failure>();

        for (EvaluatedDataset dataset : datasets) {
            AnalysisResult result = dataset.result();
            Map<String, ColumnProfile> profiles = new LinkedHashMap<>();
            result.profiles().forEach(profile -> profiles.put(profile.column().id(), profile));
            for (ExpectedColumn expected : dataset.expectedColumns()) {
                columns++;
                List<MappingCandidate> candidates = result.candidatesByColumn()
                        .getOrDefault(expected.sourceColumnId(), List.of());
                String best = candidates.isEmpty() ? null : candidates.getFirst().targetFieldId();
                boolean matchable = expected.expectedTargetFieldId() != null;
                if (matchable) {
                    mappable++;
                    if (expected.expectedTargetFieldId().equals(best)) top1++;
                    if (candidates.stream().limit(3).anyMatch(candidate ->
                            expected.expectedTargetFieldId().equals(candidate.targetFieldId()))) top3++;
                }
                boolean autoMap = simulatedAutoMap(result, expected.sourceColumnId(), candidates, policy);
                if (autoMap) {
                    simulatedAutoMaps++;
                    if (matchable && expected.expectedTargetFieldId().equals(best)) correctAutoMaps++;
                    else falsePositives++;
                }
                if (expected.shouldAbstain()) {
                    abstentionCases++;
                    if (!autoMap) correctAbstentions++;
                }
                var decision = result.decisionsByColumn().get(expected.sourceColumnId());
                if (decision != null && decision.status() == DecisionStatus.REVIEW_RECOMMENDED) reviews++;
                if (!matchable) {
                    noMatchCases++;
                    if (decision != null && decision.status() == DecisionStatus.NO_MATCH) correctNoMatches++;
                }
                if (matchable && !expected.expectedTargetFieldId().equals(best)) {
                    failures.add(new Failure(dataset.datasetId(), expected.sourceColumnId(),
                            expected.expectedTargetFieldId(), best, "expected target is not top-1"));
                } else if (!matchable && decision != null && decision.status() != DecisionStatus.NO_MATCH) {
                    failures.add(new Failure(dataset.datasetId(), expected.sourceColumnId(),
                            null, best, "expected NO_MATCH but engine retained a mapping"));
                }

                ColumnProfile profile = profiles.get(expected.sourceColumnId());
                String predictedSemantic = profile == null || profile.statistics().dominantSemanticType().isEmpty()
                        ? "UNKNOWN" : profile.statistics().dominantSemanticType();
                String expectedSemantic = expected.expectedSemanticType() == null
                        ? "UNKNOWN" : expected.expectedSemanticType();
                confusion.computeIfAbsent(expectedSemantic, ignored -> new LinkedHashMap<>())
                        .merge(predictedSemantic, 1L, Long::sum);
            }
        }
        return new EvaluationReport(datasets.size(), columns, mappable,
                top1, rate(top1, mappable), top3, rate(top3, mappable),
                simulatedAutoMaps, correctAutoMaps,
                simulatedAutoMaps == 0 ? null : rate(correctAutoMaps, simulatedAutoMaps),
                reviews, rate(reviews, columns), falsePositives, rate(falsePositives, columns),
                abstentionCases, correctAbstentions, rate(correctAbstentions, abstentionCases),
                noMatchCases, correctNoMatches, rate(correctNoMatches, noMatchCases),
                immutableMatrix(confusion), failures);
    }

    private static boolean simulatedAutoMap(AnalysisResult result, String sourceId,
            List<MappingCandidate> candidates, EngineConfig policy) {
        if (candidates.isEmpty()) return false;
        MappingCandidate best = candidates.getFirst();
        var decision = result.decisionsByColumn().get(sourceId);
        return best.eligible() && best.score() >= policy.autoMapThreshold()
                && best.coverage() >= policy.minimumCoverage()
                && decision != null && decision.margin() != null
                && decision.margin() >= policy.minimumMargin()
                && decision.blockers().stream().noneMatch(blocker -> blocker.contains("collision"));
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static Map<String, Map<String, Long>> immutableMatrix(
            Map<String, Map<String, Long>> source) {
        var result = new LinkedHashMap<String, Map<String, Long>>();
        source.forEach((key, value) -> result.put(key,
                Collections.unmodifiableMap(new LinkedHashMap<>(value))));
        return Collections.unmodifiableMap(result);
    }

    /** One analyzed dataset and its labels. */
    public record EvaluatedDataset(String datasetId, AnalysisResult result,
                                   List<ExpectedColumn> expectedColumns) {
        public EvaluatedDataset {
            Objects.requireNonNull(datasetId, "datasetId");
            Objects.requireNonNull(result, "result");
            expectedColumns = List.copyOf(expectedColumns);
        }
    }

    /** Expected target and semantic behavior for one position-based source column. */
    public record ExpectedColumn(String sourceColumnId, String expectedTargetFieldId,
                                 String expectedSemanticType, boolean shouldAbstain) {
        public ExpectedColumn { Objects.requireNonNull(sourceColumnId, "sourceColumnId"); }
    }

    /** Aggregate metrics. Rates are fractions in [0,1], never probabilities. */
    public record EvaluationReport(int datasets, long columns, long mappableColumns,
            long top1Correct, double top1Accuracy, long top3Correct, double top3Recall,
            long simulatedAutoMaps, long correctSimulatedAutoMaps,
            Double simulatedAutoMapPrecision, long reviews, double reviewRate,
            long falsePositives, double falsePositiveRate,
            long abstentionCases, long correctAbstentions, double abstentionQuality,
            long noMatchCases, long correctNoMatches, double noMatchAccuracy,
            Map<String, Map<String, Long>> semanticConfusion, List<Failure> failures) {
        public EvaluationReport {
            semanticConfusion = immutableMatrix(semanticConfusion);
            failures = List.copyOf(failures);
        }
    }

    /** A retained evaluation failure; it contains IDs only, never cell values. */
    public record Failure(String datasetId, String sourceColumnId, String expectedTargetFieldId,
                          String predictedTargetFieldId, String reason) {}
}
