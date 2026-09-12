package io.github.felipemacedo1.rizoma.api;

/** High-level outcome that keeps technical failure, invalid data and review separate. */
public enum ProcessStatus {
    /** A confirmed plan completed its dry run without data warnings or errors. */
    SUCCESS,
    /** A confirmed plan completed, but one or more data warnings require attention. */
    SUCCESS_WITH_WARNINGS,
    /** Mapping suggestions exist but no implicit confirmation was made. */
    REVIEW_REQUIRED,
    /** A confirmed plan ran, but source rows or fields were invalid. */
    INVALID,
    /** A technical source, configuration or processing failure prevented a result. */
    FAILED
}
