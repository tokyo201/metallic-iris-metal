package dev.tokyo.metalliciris.pack;

/** Thrown when a shaderpack cannot be read or is structurally invalid. */
public final class ShaderPackException extends RuntimeException {
    public ShaderPackException(String message) {
        super(message);
    }

    public ShaderPackException(String message, Throwable cause) {
        super(message, cause);
    }
}
