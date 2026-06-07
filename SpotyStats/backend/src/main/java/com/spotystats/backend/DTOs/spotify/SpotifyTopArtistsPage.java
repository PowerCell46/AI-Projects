package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * One page of {@code GET /me/top/artists} — full artist objects ordered by
 * Spotify's affinity ranking, strongest first.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyTopArtistsPage {

    private List<SpotifyArtist> items;
}
