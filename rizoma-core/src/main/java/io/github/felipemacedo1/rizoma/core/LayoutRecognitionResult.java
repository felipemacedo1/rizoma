package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Objects;

/** Auditable route decision and optional source-bound plan for the current file. */
public record LayoutRecognitionResult(String formatVersion,
        LayoutCompatibilityReport.ExecutionRoute route,
        LayoutCompatibilityReport.Classification classification,
        String sourceId, String sourceContentFingerprint, LayoutSignature currentLayout,
        String templateId, String templateVersion, LayoutCompatibilityReport compatibility,
        MappingPlan mappingPlan, long rowsReadForRecognition, long candidatePairsEvaluated,
        long similarityMetricsExecuted, boolean fullProfilingExecuted,
        boolean inferenceSkipped, List<String> reasons) {
    public LayoutRecognitionResult {
        formatVersion = formatVersion == null ? "1.0" : formatVersion;
        route = Objects.requireNonNull(route, "route");
        classification = Objects.requireNonNull(classification, "classification");
        sourceId = Objects.requireNonNull(sourceId, "sourceId");
        sourceContentFingerprint = Objects.requireNonNull(sourceContentFingerprint, "sourceContentFingerprint");
        currentLayout = Objects.requireNonNull(currentLayout, "currentLayout");
        compatibility = Objects.requireNonNull(compatibility, "compatibility");
        templateId = templateId == null ? "" : templateId;
        templateVersion = templateVersion == null ? "" : templateVersion;
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        if (rowsReadForRecognition < 0 || candidatePairsEvaluated < 0 || similarityMetricsExecuted < 0)
            throw new IllegalArgumentException("recognition counters must not be negative");
    }
}
