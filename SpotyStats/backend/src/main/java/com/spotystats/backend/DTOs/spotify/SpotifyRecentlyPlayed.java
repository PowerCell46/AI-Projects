package com.spotystats.backend.DTOs.spotify;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyTrack {

        private String id;

        private String name;

        @JsonProperty("duration_ms")
        private Integer durationMs;

        private Integer popularity;

        private SpotifyAlbum album;

        private List<SpotifyArtist> artists;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyAlbum {

        private String id;

        private String name;

        private List<SpotifyImage> images;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyArtist {

        private String id;

        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyImage {

        private String url;

        private Integer height;

        private Integer width;
    }
}
