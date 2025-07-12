package com.example.mcp.controller;

import com.example.mcp.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler for the MCP Server application.
 * This class centralizes the handling of exceptions thrown across all @Controller classes,
 * providing a consistent and structured way to return error responses to clients.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles ToolNotFoundException, returning a 404 Not Found status.
     * This exception is typically thrown when a requested tool does not exist.
     *
     * @param ex The ToolNotFoundException instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 404 status and the exception message.
     */
    @ExceptionHandler(ToolNotFoundException.class)
    public ResponseEntity<String> handleToolNotFoundException(ToolNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ConfigurationLoadingException, returning a 500 Internal Server Error status.
     * This exception indicates issues during the loading of application configurations.
     *
     * @param ex The ConfigurationLoadingException instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 500 status and the exception message.
     */
    @ExceptionHandler(ConfigurationLoadingException.class)
    public ResponseEntity<Object> handleConfigurationLoadingException(ConfigurationLoadingException ex, WebRequest request) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles ToolRegistrationException, returning a 400 Bad Request status.
     * This exception is thrown when there's an issue during the registration of a tool.
     *
     * @param ex The ToolRegistrationException instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 400 status and the exception message.
     */
    @ExceptionHandler(ToolRegistrationException.class)
    public ResponseEntity<Object> handleToolRegistrationException(ToolRegistrationException ex, WebRequest request) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ExternalApiException, returning a 500 Internal Server Error status.
     * This exception signifies an error when the application tries to communicate with an external API.
     *
     * @param ex The ExternalApiException instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 500 status and the exception message.
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<Object> handleExternalApiException(ExternalApiException ex, WebRequest request) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles InvalidOpenApiSpecException, returning a 400 Bad Request status.
     * This exception is thrown when the provided OpenAPI specification is invalid or cannot be parsed.
     *
     * @param ex The InvalidOpenApiSpecException instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 400 status and the exception message.
     */
    @ExceptionHandler(InvalidOpenApiSpecException.class)
    public ResponseEntity<Object> handleInvalidOpenApiSpecException(InvalidOpenApiSpecException ex, WebRequest request) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Catches any other unexpected exceptions, returning a generic 500 Internal Server Error.
     * This acts as a fallback for unhandled exceptions.
     *
     * @param ex The Exception instance.
     * @param request The current web request.
     * @return A ResponseEntity with a 500 status and a generic error message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception ex, WebRequest request) {
        // Log the exception for debugging purposes
        logger.error("An unexpected error occurred: " + ex.getMessage(), ex);
        return new ResponseEntity<>("An unexpected error occurred. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}