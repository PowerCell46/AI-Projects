package com.spotystats.backend.dtos.profile;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;


/**
 * Profile data exposed to the SPA's profile page: Spotify account details
 * plus what SpotyStats has recorded about the user's listening.
 */
@Value
@Builder
public class ProfileResponse {

    String spotifyUserId;

    String displayName;

    String email;

    String imageUrl;

    String country;

    String product;

    Integer followers;

    ListeningTotals totals;

    /**
     * All-time aggregates over the plays SpotyStats has recorded.
     * {@code likedTotal} comes live from Spotify and is null when unavailable.
     */
    @Value
    @Builder
    public static class ListeningTotals {

        long totalPlays;

        long totalListeningTimeMs;

        long uniqueArtists;

        long uniqueTracks;

        LocalDate trackingSince;

        Long likedTotal;
    }
}
