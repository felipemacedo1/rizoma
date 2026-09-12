package io.github.felipemacedo1.rizoma.core;

/** Bounded data-error policy for a dry run. */
public record DryRunOptions(ErrorPolicy errorPolicy, long maxErrors, int maxIssueSamples,
                            int maxDistinctIssueCodes) {
    public DryRunOptions {
        if (errorPolicy == null) errorPolicy = ErrorPolicy.COLLECT_ERRORS;
        if (maxErrors <= 0 || maxIssueSamples < 0 || maxDistinctIssueCodes <= 0)
            throw new IllegalArgumentException("invalid dry-run limits");
    }
    public DryRunOptions(ErrorPolicy errorPolicy, long maxErrors, int maxIssueSamples) {
        this(errorPolicy, maxErrors, maxIssueSamples, 256);
    }
    public static DryRunOptions defaults() {
        return new DryRunOptions(ErrorPolicy.COLLECT_ERRORS, 1_000, 100, 256);
    }
    public enum ErrorPolicy { FAIL_FAST, SKIP_ROW, COLLECT_ERRORS }
}
