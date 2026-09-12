package io.github.felipemacedo1.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.felipemacedo1.rizoma.core.AnalysisResult.DecisionStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingQualityEvaluatorTest {
    @Test void computesRankingAbstentionNoMatchAndSemanticConfusion() {
        var email = candidate("email", .95, .8);
        var other = candidate("other", .7, .8);
        var result = new AnalysisResult("1.2", "test", "UNCALIBRATED", "source", "sha",
                "schema", "1", "schema-sha", "config", "config-sha",
                new DataReader.SourceStructure("TEST", "", "", true, List.of(
                        new DataReader.SourceColumn("c0", 0, "Email", "email"),
                        new DataReader.SourceColumn("c1", 1, "Unknown", "unknown")), List.of()),
                20, List.of(profile("c0", "core:email"), profile("c1", "")),
                Map.of("c0", List.of(email, other), "c1", List.of(other)), Map.of(),
                Map.of("c0", decision("c0", "email", .95, .2, DecisionStatus.REVIEW_RECOMMENDED),
                        "c1", decision("c1", null, .4, null, DecisionStatus.NO_MATCH)),
                List.of("c1"), List.of(), List.of(), List.of());
        var labels = List.of(
                new MappingQualityEvaluator.ExpectedColumn("c0", "email", "core:email", false),
                new MappingQualityEvaluator.ExpectedColumn("c1", null, null, true));

        var report = new MappingQualityEvaluator().evaluate(List.of(
                new MappingQualityEvaluator.EvaluatedDataset("test", result, labels)), EngineConfig.defaults());

        assertEquals(1, report.top1Accuracy());
        assertEquals(1, report.top3Recall());
        assertEquals(1, report.simulatedAutoMaps());
        assertEquals(1, report.simulatedAutoMapPrecision());
        assertEquals(1, report.abstentionQuality());
        assertEquals(1, report.noMatchAccuracy());
        assertEquals(1, report.semanticConfusion().get("core:email").get("core:email"));
    }

    private static AnalysisResult.MappingCandidate candidate(String target, double score, double coverage) {
        return new AnalysisResult.MappingCandidate(target, target, score, coverage, score * coverage,
                true, List.of(), List.of());
    }

    private static AnalysisResult.MappingDecision decision(String source, String target, double score,
            Double margin, DecisionStatus status) {
        return new AnalysisResult.MappingDecision(source, target, status, score, .8, margin,
                score * .8, List.of(), List.of("AUTO_MAP is disabled by core configuration"));
    }

    private static ColumnProfile profile(String id, String semantic) {
        var statistics = new ColumnProfile.ColumnStatistics(
                new ColumnProfile.Cardinality(20, ColumnProfile.MeasureAccuracy.EXACT, "test", null),
                1, List.of(), 4.3, ColumnProfile.MeasureAccuracy.EXACT, 0.0,
                null, Map.of(), Map.of(), 0, 0, semantic, semantic.isEmpty() ? 0 : 1,
                semantic.isEmpty() ? 0 : 1, semantic.isEmpty() ? 0 : 0, List.of());
        return new ColumnProfile(new DataReader.SourceColumn(id, 0, id, id), 20, 0,
                1, 10, 5, Map.of(PhysicalType.TEXT, 20L), PhysicalType.TEXT,
                Map.of(), List.of(), ColumnProfile.MeasureAccuracy.EXACT, "test", statistics);
    }
}
