package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * Subset of Spotify's "Get User's Saved Tracks" ({@code GET /me/tracks}) response.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifySavedTracksPage {

    private Integer total;

    private List<SavedTrackItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SavedTrackItem {

        @JsonProperty("added_at")
        private String addedAt;

        private SpotifyTrack track;
    }
}
