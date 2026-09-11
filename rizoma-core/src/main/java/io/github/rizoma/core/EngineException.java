package io.github.rizoma.core;

/** Safe, coded failure raised by the engine without embedding source values. */
public final class EngineException extends RuntimeException {
    private final String code;

    public EngineException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EngineException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
