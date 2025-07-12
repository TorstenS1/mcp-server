package com.example.mcp.exception;

public class ConfigurationLoadingException extends RuntimeException {
    public ConfigurationLoadingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationLoadingException(String message) {
        super(message);
    }
}