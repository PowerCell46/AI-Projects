package com.spotystats.backend.DTOs.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Subset of Spotify's "Get Current User's Profile" ({@code GET /me}) response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyUserProfile(

        String id,

        @JsonProperty("display_name")
        String displayName,

        String email,

        List<SpotifyImage> images
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpotifyImage(

            String url,

            Integer height,

            Integer width
    ) {
    }
}
