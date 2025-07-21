package de.augmentia.example.mcp.exception;

public class InvalidOpenApiSpecException extends RuntimeException {
    public InvalidOpenApiSpecException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidOpenApiSpecException(String message) {
        super(message);
    }
}