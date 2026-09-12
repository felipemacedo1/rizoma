package io.github.felipemacedo1.rizoma.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact, immutable evidence projection consumed by candidate scoring. */
public record ColumnFeatures(DataReader.SourceColumn column, String normalizedName,
        String compactName, List<String> nameTokens, PhysicalType inferredType,
        long observedValues, Map<String, SemanticDetector.SemanticEvidence> semanticEvidence) {
    public ColumnFeatures {
        nameTokens = List.copyOf(nameTokens);
        semanticEvidence = Collections.unmodifiableMap(new LinkedHashMap<>(semanticEvidence));
    }
}
