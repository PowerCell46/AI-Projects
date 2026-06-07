package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * Spotify artist object. The simplified form embedded in tracks carries only
 * id and name; the full form ({@code GET /artists/{id}}) also has images.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyArtist {

    private String id;

    private String name;

    private List<SpotifyImage> images;
}
