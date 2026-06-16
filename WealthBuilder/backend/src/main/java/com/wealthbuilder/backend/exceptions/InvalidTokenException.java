package com.wealthbuilder.backend.exceptions;


/**
 * Raised when a bearer token cannot be trusted — malformed, wrong signature, or expired.
 * Callers treat it as "not authenticated" rather than surfacing the specific cause.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
