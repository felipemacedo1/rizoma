package io.github.felipemacedo1.rizoma.api;

/** Base public exception for technical API failures; data errors remain in results. */
public class RizomaException extends RuntimeException {
    private final String code;

    /** Creates a coded exception whose message must not contain source cell values. */
    public RizomaException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        this.code = code;
    }

    /** Stable machine-readable technical error code. */
    public String code() { return code; }
}
