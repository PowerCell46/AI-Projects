package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * Spotify's track object as it appears inside recently-played items and the
 * saved-tracks library.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyTrack {

    private String id;

    private String name;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    private Integer popularity;

    private SpotifyAlbum album;

    private List<SpotifyArtist> artists;
}
