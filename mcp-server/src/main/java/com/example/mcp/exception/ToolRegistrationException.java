package com.example.mcp.exception;

public class ToolRegistrationException extends RuntimeException {
    public ToolRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ToolRegistrationException(String message) {
        super(message);
    }
}