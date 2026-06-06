package com.spotystats.backend.exceptions;

import com.spotystats.backend.DTOs.error.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Errors bubbling up from the Spotify API. A 401 is surfaced as-is so the SPA can
     * prompt re-authentication; anything else is reported as a gateway failure.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiError> handleUpstream(RestClientResponseException ex) {
        log.warn("Spotify API responded with {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());

        if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            return build(HttpStatus.UNAUTHORIZED, "spotify_unauthorized",
                    "Spotify authorization is no longer valid; please sign in again.");
        }

        return build(HttpStatus.BAD_GATEWAY, "spotify_error", "The Spotify API request failed.");
    }

    /**
     * Raised when there is no authorized Spotify client for the current request.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state handling request: {}", ex.getMessage());

        return build(HttpStatus.UNAUTHORIZED, "not_authorized", "No active Spotify session.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled error handling request", ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred.");
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiError.of(status.value(), error, message));
    }
}
