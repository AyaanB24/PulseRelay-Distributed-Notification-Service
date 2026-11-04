package com.pulserelay.exception;

/**
 * Exception thrown when API key operations fail or authentication fails.
 */
public class ApiKeyException extends RuntimeException {

    public ApiKeyException(String message) {
        super(message);
    }

    public ApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
