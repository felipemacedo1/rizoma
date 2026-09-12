package io.github.rizoma.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, versioned instructions for transforming and validating a previously analyzed source. */
public record MappingPlan(String formatVersion, String planId, String engineVersion,
        String sourceId, String sourceFingerprint, String schemaId, String schemaVersion,
        String schemaFingerprint, String configurationVersion, String configurationFingerprint,
        String knowledgeSnapshotId, String knowledgeVersion,
        List<FieldMapping> mappings, List<String> unmappedSourceColumns,
        List<String> confirmedSourceColumns) {
    public MappingPlan {
        requireText(formatVersion, "formatVersion"); requireText(planId, "planId");
        requireText(engineVersion, "engineVersion"); requireText(sourceId, "sourceId");
        requireText(sourceFingerprint, "sourceFingerprint"); requireText(schemaId, "schemaId");
        requireText(schemaVersion, "schemaVersion"); requireText(schemaFingerprint, "schemaFingerprint");
        requireText(configurationVersion, "configurationVersion");
        requireText(configurationFingerprint, "configurationFingerprint");
        knowledgeSnapshotId = knowledgeSnapshotId == null ? NoOpMappingKnowledgeBase.SNAPSHOT_ID : knowledgeSnapshotId;
        knowledgeVersion = knowledgeVersion == null ? "1.0" : knowledgeVersion;
        requireText(knowledgeSnapshotId, "knowledgeSnapshotId");
        requireText(knowledgeVersion, "knowledgeVersion");
        mappings = List.copyOf(mappings); unmappedSourceColumns = List.copyOf(unmappedSourceColumns);
        confirmedSourceColumns = List.copyOf(confirmedSourceColumns);
    }

    /** Backward-compatible constructor for MappingPlan 1.0 without knowledge metadata. */
    public MappingPlan(String formatVersion, String planId, String engineVersion,
            String sourceId, String sourceFingerprint, String schemaId, String schemaVersion,
            String schemaFingerprint, String configurationVersion, String configurationFingerprint,
            List<FieldMapping> mappings, List<String> unmappedSourceColumns,
            List<String> confirmedSourceColumns) {
        this(formatVersion, planId, engineVersion, sourceId, sourceFingerprint, schemaId,
                schemaVersion, schemaFingerprint, configurationVersion, configurationFingerprint,
                NoOpMappingKnowledgeBase.SNAPSHOT_ID, "1.0", mappings, unmappedSourceColumns,
                confirmedSourceColumns);
    }

    /** One explicit source-to-target mapping and its ordered execution steps. */
    public record FieldMapping(String sourceColumnId, String targetFieldId,
            List<Step> transformations, List<Step> validations,
            boolean confirmed, String confirmationReason) {
        public FieldMapping {
            requireText(sourceColumnId, "sourceColumnId"); requireText(targetFieldId, "targetFieldId");
            transformations = List.copyOf(transformations == null ? List.of() : transformations);
            validations = List.copyOf(validations == null ? List.of() : validations);
            confirmationReason = confirmationReason == null ? "" : confirmationReason;
            if (!confirmed) throw new IllegalArgumentException("mapping must be explicitly confirmed");
        }
    }

    /** Versioned transformer or validator reference with small string configuration. */
    public record Step(String id, String version, Map<String, String> options) {
        public Step {
            requireText(id, "step id"); requireText(version, "step version");
            options = Map.copyOf(options == null ? Map.of() : options);
        }
        public Step(String id) { this(id, "1", Map.of()); }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
