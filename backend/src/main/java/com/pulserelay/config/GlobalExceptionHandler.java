package com.pulserelay.config;

import com.pulserelay.exception.ApiKeyException;
import com.pulserelay.exception.NotificationException;
import com.pulserelay.exception.TenantException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error response formatting.
 * Handles validation errors, custom exceptions, and unexpected errors.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle Bean Validation errors (invalid request body).
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("details", errors);
        response.put("status", HttpStatus.BAD_REQUEST.value());

        log.debug("Validation error: {}", errors);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * Handle NotificationException.
     * Returns 400 Bad Request for validation/business logic errors.
     */
    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<Map<String, Object>> handleNotificationException(NotificationException exception) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", exception.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());

        log.debug("Notification error: {}", exception.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * Handle ApiKeyException.
     * Returns 401 Unauthorized for authentication failures.
     */
    @ExceptionHandler(ApiKeyException.class)
    public ResponseEntity<Map<String, Object>> handleApiKeyException(ApiKeyException exception) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", exception.getMessage());
        response.put("status", HttpStatus.UNAUTHORIZED.value());

        log.debug("API key error: {}", exception.getMessage());

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(response);
    }

    /**
     * Handle TenantException.
     * Returns 400 Bad Request or 404 Not Found depending on context.
     */
    @ExceptionHandler(TenantException.class)
    public ResponseEntity<Map<String, Object>> handleTenantException(TenantException exception) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", exception.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());

        log.debug("Tenant error: {}", exception.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }

    /**
     * Handle unexpected exceptions.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception exception) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal server error");
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        log.error("Unexpected error", exception);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
