package io.github.felipemacedo1.rizoma.api;

import io.github.felipemacedo1.rizoma.core.AnalysisResult;
import io.github.felipemacedo1.rizoma.core.DryRunResult;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionResult;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable consumer-oriented view of one process invocation, with optional
 * access to detailed workflow results and no source cell values.
 */
public final class ProcessResult {
    private final ProcessStatus status;
    private final ProcessRoute route;
    private final MappingSummary mappingSummary;
    private final ProfilingSummary profilingSummary;
    private final AnalysisResult analysis;
    private final LayoutRecognitionResult recognition;
    private final MappingPlan mappingPlan;
    private final DryRunResult dryRun;
    private final List<ProcessIssue> warnings;
    private final List<ProcessIssue> errors;

    ProcessResult(ProcessStatus status, ProcessRoute route, MappingSummary mappingSummary,
            ProfilingSummary profilingSummary, AnalysisResult analysis,
            LayoutRecognitionResult recognition, MappingPlan mappingPlan, DryRunResult dryRun,
            List<ProcessIssue> warnings, List<ProcessIssue> errors) {
        this.status = Objects.requireNonNull(status);
        this.route = Objects.requireNonNull(route);
        this.mappingSummary = Objects.requireNonNull(mappingSummary);
        this.profilingSummary = Objects.requireNonNull(profilingSummary);
        this.analysis = analysis;
        this.recognition = recognition;
        this.mappingPlan = mappingPlan;
        this.dryRun = dryRun;
        this.warnings = List.copyOf(warnings);
        this.errors = List.copyOf(errors);
    }

    /** High-level outcome. */
    public ProcessStatus status() { return status; }
    /** Route actually selected. */
    public ProcessRoute route() { return route; }
    /** Small mapping view suitable for a UI or integration decision. */
    public MappingSummary mappingSummary() { return mappingSummary; }
    /** Small profiling view; detailed profiles remain in {@link #analysis()}. */
    public ProfilingSummary profilingSummary() { return profilingSummary; }
    /** Detailed analysis when the selected route performed one. */
    public Optional<AnalysisResult> analysis() { return Optional.ofNullable(analysis); }
    /** Detailed layout recognition when a registry or template was consulted. */
    public Optional<LayoutRecognitionResult> recognition() { return Optional.ofNullable(recognition); }
    /** Confirmed source-bound plan used or instantiated for this source. */
    public Optional<MappingPlan> mappingPlan() { return Optional.ofNullable(mappingPlan); }
    /** Dry-run details when a confirmed plan was available. */
    public Optional<DryRunResult> dryRun() { return Optional.ofNullable(dryRun); }
    /** Bounded safe warning summaries. */
    public List<ProcessIssue> warnings() { return warnings; }
    /** Bounded safe technical or data-error summaries. */
    public List<ProcessIssue> errors() { return errors; }
    /** Whether human mapping review is required before dry run. */
    public boolean reviewRequired() { return status == ProcessStatus.REVIEW_REQUIRED; }
    /** Whether a confirmed plan completed without invalid rows. */
    public boolean canContinue() {
        return status == ProcessStatus.SUCCESS || status == ProcessStatus.SUCCESS_WITH_WARNINGS;
    }
    /** Number of valid rows, or zero when dry run did not execute. */
    public long validRows() { return dryRun == null ? 0 : dryRun.rowsValid(); }
    /** Number of valid rows carrying warnings. */
    public long validRowsWithWarnings() { return dryRun == null ? 0 : dryRun.rowsValidWithWarnings(); }
    /** Number of invalid rows. */
    public long invalidRows() { return dryRun == null ? 0 : dryRun.rowsInvalid(); }
    /** Number of skipped rows. */
    public long skippedRows() { return dryRun == null ? 0 : dryRun.rowsSkipped(); }

    /** Safe aggregated issue; count is never represented by repeated objects. */
    public record ProcessIssue(String code, String message, long count) {
        public ProcessIssue {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("issue code must not be blank");
            message = message == null ? "" : message;
            if (count < 0) throw new IllegalArgumentException("issue count must not be negative");
        }
    }

    /** Consumer-oriented mapping summary without candidate-scoring internals. */
    public record MappingSummary(int suggestedMappings, int confirmedMappings,
            int ignoredSourceColumns, int unmappedSourceColumns,
            int unresolvedRequiredTargets, List<MappingSuggestion> suggestions) {
        public MappingSummary {
            if (suggestedMappings < 0 || confirmedMappings < 0 || ignoredSourceColumns < 0
                    || unmappedSourceColumns < 0 || unresolvedRequiredTargets < 0)
                throw new IllegalArgumentException("mapping counts must not be negative");
            suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
        }
        /** Empty summary for a technical failure before mapping. */
        public static MappingSummary empty() { return new MappingSummary(0, 0, 0, 0, 0, List.of()); }
    }

    /** One recommendation. Score and confidence index remain uncalibrated heuristics. */
    public record MappingSuggestion(String sourceColumnId, String sourceHeader,
            String targetFieldId, String decision, double score, double coverage,
            Double margin, List<String> reasons, List<String> blockers) {
        public MappingSuggestion {
            sourceColumnId = sourceColumnId == null ? "" : sourceColumnId;
            sourceHeader = sourceHeader == null ? "" : sourceHeader;
            targetFieldId = targetFieldId == null ? "" : targetFieldId;
            decision = decision == null ? "" : decision;
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
            blockers = List.copyOf(blockers == null ? List.of() : blockers);
        }
    }

    /** Small bounded profile summary for the common path. */
    public record ProfilingSummary(long rowsProcessed, int columnsProfiled,
            int warningCount, boolean fullProfilingExecuted) {
        public ProfilingSummary {
            if (rowsProcessed < 0 || columnsProfiled < 0 || warningCount < 0)
                throw new IllegalArgumentException("profiling counts must not be negative");
        }
        /** Empty summary for routes that did not perform full analysis. */
        public static ProfilingSummary empty() { return new ProfilingSummary(0, 0, 0, false); }
    }
}
