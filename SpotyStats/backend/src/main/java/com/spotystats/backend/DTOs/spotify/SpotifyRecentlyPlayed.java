package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * Subset of Spotify's "Get Recently Played Tracks"
 * ({@code GET /me/player/recently-played}) response.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyRecentlyPlayed {

    private List<PlayHistoryItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayHistoryItem {

        private SpotifyTrack track;

        @JsonProperty("played_at")
        private String playedAt;
    }
}
