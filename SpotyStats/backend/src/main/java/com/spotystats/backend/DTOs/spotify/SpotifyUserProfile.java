package com.spotystats.backend.dtos.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * Subset of Spotify's "Get Current User's Profile" ({@code GET /me}) response.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyUserProfile {

    private String id;

    @JsonProperty("display_name")
    private String displayName;

    private String email;

    private String country;

    private String product;

    private Followers followers;

    private List<SpotifyImage> images;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Followers {

        private Integer total;
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
