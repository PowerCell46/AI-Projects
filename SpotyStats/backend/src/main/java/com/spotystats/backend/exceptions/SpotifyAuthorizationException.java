package com.spotystats.backend.exceptions;

/**
 * Raised when no authorized Spotify client exists for the current user — the session is
 * anonymous or the stored authorization is gone. Mapped to 401 {@code not_authorized} by
 * the global exception handler so the SPA can prompt a re-login.
 */
public class SpotifyAuthorizationException extends RuntimeException {

    public SpotifyAuthorizationException(String message) {
        super(message);
    }
}
