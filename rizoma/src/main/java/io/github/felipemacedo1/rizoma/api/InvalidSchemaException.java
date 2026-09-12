package io.github.felipemacedo1.rizoma.api;

/** Target schema is missing or inconsistent with the requested workflow. */
public final class InvalidSchemaException extends RizomaException {
    /** Creates a safe invalid-schema error. */
    public InvalidSchemaException(String code, String message) { super(code, message); }
}
