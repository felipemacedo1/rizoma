package io.github.rizoma.core;

import java.util.List;
import java.util.Map;

/** Complete, JSON-friendly and explainable output of analysis. */
public record AnalysisResult(String formatVersion, String engineVersion, String calibration,
        String sourceId, String sourceFingerprint, String schemaId, String schemaVersion,
        String schemaFingerprint, String configurationVersion, String configurationFingerprint,
        DataReader.SourceStructure structure, long rowsProcessed, List<ColumnProfile> profiles,
        Map<String, List<MappingCandidate>> candidatesByColumn,
        Map<String, List<PrunedCandidate>> prunedCandidatesByColumn,
        Map<String, MappingDecision> decisionsByColumn, List<String> unmatchedColumns,
        List<String> conflicts, List<String> warnings, List<SafeError> errors,
        String knowledgeSnapshotId, String knowledgeVersion,
        Map<String, List<HistoricalEvidence>> historicalEvidenceByColumn) {
    public AnalysisResult {
        profiles = List.copyOf(profiles); candidatesByColumn = immutableLists(candidatesByColumn);
        prunedCandidatesByColumn = immutableLists(prunedCandidatesByColumn == null ? Map.of() : prunedCandidatesByColumn);
        decisionsByColumn = Map.copyOf(decisionsByColumn); unmatchedColumns = List.copyOf(unmatchedColumns);
        conflicts = List.copyOf(conflicts); warnings = List.copyOf(warnings); errors = List.copyOf(errors);
        knowledgeSnapshotId = knowledgeSnapshotId == null ? NoOpMappingKnowledgeBase.SNAPSHOT_ID : knowledgeSnapshotId;
        knowledgeVersion = knowledgeVersion == null ? "1.0" : knowledgeVersion;
        if (knowledgeSnapshotId.isBlank() || knowledgeVersion.isBlank())
            throw new IllegalArgumentException("knowledge snapshot metadata must not be blank");
        historicalEvidenceByColumn = immutableLists(historicalEvidenceByColumn == null
                ? Map.of() : historicalEvidenceByColumn);
    }

    /** Backward-compatible constructor for AnalysisResult JSON/API versions 1.0 through 1.2. */
    public AnalysisResult(String formatVersion, String engineVersion, String calibration,
            String sourceId, String sourceFingerprint, String schemaId, String schemaVersion,
            String schemaFingerprint, String configurationVersion, String configurationFingerprint,
            DataReader.SourceStructure structure, long rowsProcessed, List<ColumnProfile> profiles,
            Map<String, List<MappingCandidate>> candidatesByColumn,
            Map<String, List<PrunedCandidate>> prunedCandidatesByColumn,
            Map<String, MappingDecision> decisionsByColumn, List<String> unmatchedColumns,
            List<String> conflicts, List<String> warnings, List<SafeError> errors) {
        this(formatVersion, engineVersion, calibration, sourceId, sourceFingerprint, schemaId,
                schemaVersion, schemaFingerprint, configurationVersion, configurationFingerprint,
                structure, rowsProcessed, profiles, candidatesByColumn, prunedCandidatesByColumn,
                decisionsByColumn, unmatchedColumns, conflicts, warnings, errors,
                NoOpMappingKnowledgeBase.SNAPSHOT_ID, "1.0", Map.of());
    }

    private static <T> Map<String, List<T>> immutableLists(Map<String, List<T>> source) {
        var copy = new java.util.LinkedHashMap<String, List<T>>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    public record ScoreComponent(String id, boolean available, Double value, double weight,
            double reliability, double contribution, String evidence, String unavailableReason,
            List<String> limitations) {
        public ScoreComponent { limitations = List.copyOf(limitations); }
    }
    public record MappingCandidate(String targetFieldId, String targetDisplayName, double score,
            double coverage, double confidenceIndex, boolean eligible,
            List<ScoreComponent> components, List<String> contradictions) {
        public MappingCandidate { components = List.copyOf(components); contradictions = List.copyOf(contradictions); }
    }
    /** Candidate excluded before expensive scoring, with a safe deterministic reason. */
    public record PrunedCandidate(String targetFieldId, String reason) {}
    public record MappingDecision(String sourceColumnId, String targetFieldId, DecisionStatus status,
            double score, double coverage, Double margin, double confidenceIndex,
            List<String> reasons, List<String> blockers) {
        public MappingDecision { reasons = List.copyOf(reasons); blockers = List.copyOf(blockers); }
    }
    public enum DecisionStatus { AUTO_MAP, REVIEW_RECOMMENDED, LOW_CONFIDENCE, NO_MATCH }
    public record SafeError(String code, String message, Long recordNumber, Long physicalLine) {}
}
