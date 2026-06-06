package com.spotystats.backend.DTOs.auth;

/**
 * Lightweight auth-state payload for {@code GET /api/me}. The SPA uses {@code authenticated}
 * to decide whether to show the "Sign in with Spotify" button.
 */
public record CurrentUserResponse(

        boolean authenticated,

        String spotifyUserId,

        String displayName
) {
    public static CurrentUserResponse anonymous() {
        return new CurrentUserResponse(false, null, null);
    }

    public static CurrentUserResponse authenticated(String spotifyUserId, String displayName) {
        return new CurrentUserResponse(true, spotifyUserId, displayName);
    }
}
