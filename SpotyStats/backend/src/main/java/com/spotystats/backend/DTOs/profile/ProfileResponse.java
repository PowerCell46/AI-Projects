package com.spotystats.backend.DTOs.profile;

/**
 * Profile data exposed to the SPA's profile page.
 */
public record ProfileResponse(

        String spotifyUserId,

        String displayName,

        String email,

        String imageUrl
) {
}
