package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Objects;

/** Structured, privacy-safe comparison between one template and a current layout probe. */
public record LayoutCompatibilityReport(String formatVersion, Classification classification,
        ExecutionRoute recommendedRoute, List<ColumnMatch> matchedColumns,
        List<Drift> drift, List<String> affectedBindings, List<String> reasons,
        boolean templateCompatible, boolean fieldIdentitiesUnambiguous) {
    public LayoutCompatibilityReport {
        formatVersion = formatVersion == null ? "1.0" : formatVersion;
        classification = Objects.requireNonNull(classification, "classification");
        recommendedRoute = Objects.requireNonNull(recommendedRoute, "recommendedRoute");
        matchedColumns = List.copyOf(matchedColumns == null ? List.of() : matchedColumns);
        drift = List.copyOf(drift == null ? List.of() : drift);
        affectedBindings = List.copyOf(affectedBindings == null ? List.of() : affectedBindings);
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }

    /** One expected-to-current structural rebind. */
    public record ColumnMatch(String expectedSourceColumnId, String currentSourceColumnId,
            String structuralKey, boolean reordered, boolean adaptivelyRebound) {}

    /** One drift item containing metadata only, never a source cell. */
    public record Drift(DriftType type, String expectedSourceColumnId,
            String currentSourceColumnId, String reason) {}

    public enum Classification { EXACT, COMPATIBLE, DRIFTED, UNKNOWN }
    public enum ExecutionRoute { FULL_ANALYSIS, FAST_REUSE, ADAPTIVE_REANALYSIS }
    public enum DriftType {
        ADDED_COLUMN, REMOVED_COLUMN, RENAMED_OR_UNKNOWN_COLUMN, REORDERED_COLUMN,
        TYPE_DRIFT, SEMANTIC_DRIFT, DUPLICATE_HEADER, REQUIRED_SOURCE_MISSING,
        STRUCTURE_METADATA_CHANGED, TARGET_SCHEMA_CHANGED, CONFIGURATION_CHANGED,
        TEMPLATE_VERSION_UNSUPPORTED
    }
}
