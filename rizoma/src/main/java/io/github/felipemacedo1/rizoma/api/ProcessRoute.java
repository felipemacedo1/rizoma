package io.github.felipemacedo1.rizoma.api;

/** Route selected by the high-level process workflow. */
public enum ProcessRoute {
    /** No route could start because a technical failure happened first. */
    NOT_STARTED,
    /** Full profiling, inference and review path. */
    FULL_ANALYSIS,
    /** A confirmed plan supplied by the caller went directly to dry run. */
    CONFIRMED_PLAN,
    /** A known layout was safely reused after bounded guards. */
    FAST_REUSE,
    /** Only bindings affected by localized drift were re-evaluated. */
    ADAPTIVE_REANALYSIS
}
