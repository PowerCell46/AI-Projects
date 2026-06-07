package com.spotystats.backend.dtos.error;

import lombok.Value;

import java.time.Instant;


/**
 * Consistent error body returned to the SPA. Deliberately free of stack traces or
 * upstream internals.
 */
@Value
public class ApiError {

    Instant timestamp;

    int status;

    String error;

    String message;

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
