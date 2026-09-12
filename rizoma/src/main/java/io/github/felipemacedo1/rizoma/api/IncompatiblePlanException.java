package io.github.felipemacedo1.rizoma.api;

/** A mapping plan or layout template does not match the current execution. */
public final class IncompatiblePlanException extends RizomaException {
    /** Creates a safe plan-compatibility error. */
    public IncompatiblePlanException(String code, String message) { super(code, message); }
}
