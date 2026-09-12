package io.github.felipemacedo1.rizoma.api;

/** Technical processing failure not represented as invalid source data. */
public final class ProcessingException extends RizomaException {
    /** Creates a safe processing error. */
    public ProcessingException(String code, String message) { super(code, message); }
}
