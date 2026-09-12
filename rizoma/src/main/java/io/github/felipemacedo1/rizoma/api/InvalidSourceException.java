package io.github.felipemacedo1.rizoma.api;

/** Source cannot be opened, identified or safely represented. */
public final class InvalidSourceException extends RizomaException {
    /** Creates a safe invalid-source error. */
    public InvalidSourceException(String code, String message) { super(code, message); }
}
