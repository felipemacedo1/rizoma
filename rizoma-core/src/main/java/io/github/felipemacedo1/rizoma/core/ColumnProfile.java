package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Map;

/** Bounded, public-safe profile of one source column. */
public record ColumnProfile(DataReader.SourceColumn column, long rowCount, long nullCount,
                            int minLength, int maxLength, double averageLength,
                            Map<PhysicalType, Long> physicalTypeVotes,
                            PhysicalType inferredType,
                            Map<String, SemanticDetector.SemanticEvidence> semanticEvidence,
                            List<String> protectedSamples, MeasureAccuracy accuracy,
                            String provenance, ColumnStatistics statistics) {
    public ColumnProfile {
        physicalTypeVotes = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(physicalTypeVotes));
        semanticEvidence = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(semanticEvidence));
        protectedSamples = List.copyOf(protectedSamples);
        statistics = statistics == null ? ColumnStatistics.unavailable(rowCount, nullCount) : statistics;
    }

    /** Backward-compatible constructor for reports and callers from 0.1. */
    public ColumnProfile(DataReader.SourceColumn column, long rowCount, long nullCount,
                         int minLength, int maxLength, double averageLength,
                         Map<PhysicalType, Long> physicalTypeVotes, PhysicalType inferredType,
                         Map<String, SemanticDetector.SemanticEvidence> semanticEvidence,
                         List<String> protectedSamples, MeasureAccuracy accuracy, String provenance) {
        this(column, rowCount, nullCount, minLength, maxLength, averageLength,
                physicalTypeVotes, inferredType, semanticEvidence, protectedSamples,
                accuracy, provenance, null);
    }

    /** Accuracy and provenance category for one profile measure. */
    public enum MeasureAccuracy { EXACT, ESTIMATED, SAMPLED, UNAVAILABLE }

    /** Advanced bounded statistics introduced in analysis report 1.2. */
    public record ColumnStatistics(Cardinality cardinality, double uniqueRatio,
            List<FrequentValue> topValues, Double entropyBits,
            MeasureAccuracy entropyAccuracy, Double entropyErrorBound,
            NumericSummary numericSummary, Map<String, Long> lengthDistribution,
            Map<String, Long> patternDistribution, double mixedTypeRatio,
            double nullRatio, String dominantSemanticType, double semanticConfidence,
            double semanticValidRatio, double semanticInvalidRatio,
            List<ColumnAnomaly> anomalies) {
        public ColumnStatistics {
            topValues = List.copyOf(topValues == null ? List.of() : topValues);
            lengthDistribution = Map.copyOf(lengthDistribution == null ? Map.of() : lengthDistribution);
            patternDistribution = Map.copyOf(patternDistribution == null ? Map.of() : patternDistribution);
            dominantSemanticType = dominantSemanticType == null ? "" : dominantSemanticType;
            anomalies = List.copyOf(anomalies == null ? List.of() : anomalies);
        }

        static ColumnStatistics unavailable(long rows, long nulls) {
            return new ColumnStatistics(new Cardinality(0, MeasureAccuracy.UNAVAILABLE,
                    "not present in report format before 1.2", null), 0, List.of(), null,
                    MeasureAccuracy.UNAVAILABLE, null, null, Map.of(), Map.of(), 0,
                    rows == 0 ? 0 : (double) nulls / rows, "", 0, 0, 0, List.of());
        }
    }

    /** Exact or estimated number of distinct nonblank values. */
    public record Cardinality(long value, MeasureAccuracy accuracy, String method,
                              Double expectedRelativeError) {}

    /** Bounded frequent-value entry; source content is always protected. */
    public record FrequentValue(int rank, String protectedValue, long estimatedCount,
                                long errorUpperBound, MeasureAccuracy accuracy) {}

    /** Incremental numeric statistics over values classified as numeric. */
    public record NumericSummary(long count, double mean, double variance,
                                 double standardDeviation, double minimum, double maximum,
                                 MeasureAccuracy accuracy) {}

    /** Aggregate anomaly with bounded, protected locations. */
    public record ColumnAnomaly(String code, long count, double ratio,
                                MeasureAccuracy accuracy, String explanation,
                                List<AnomalyLocation> locations) {
        public ColumnAnomaly { locations = List.copyOf(locations == null ? List.of() : locations); }
    }

    /** Record and physical-line location without raw source data. */
    public record AnomalyLocation(long recordNumber, long physicalLine, String protectedValue) {}
}
