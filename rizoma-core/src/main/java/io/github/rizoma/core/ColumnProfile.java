package io.github.rizoma.core;

import java.util.List;
import java.util.Map;

/** Bounded, public-safe profile of one source column. */
public record ColumnProfile(DataReader.SourceColumn column, long rowCount, long nullCount,
                            int minLength, int maxLength, double averageLength,
                            Map<PhysicalType, Long> physicalTypeVotes,
                            PhysicalType inferredType,
                            Map<String, SemanticDetector.SemanticEvidence> semanticEvidence,
                            List<String> protectedSamples, MeasureAccuracy accuracy,
                            String provenance) {
    public ColumnProfile {
        physicalTypeVotes = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(physicalTypeVotes));
        semanticEvidence = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(semanticEvidence));
        protectedSamples = List.copyOf(protectedSamples);
    }
    public enum MeasureAccuracy { EXACT, SAMPLED, UNAVAILABLE }
}
