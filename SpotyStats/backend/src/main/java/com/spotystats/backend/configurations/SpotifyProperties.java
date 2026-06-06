package com.spotystats.backend.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Settings bound from the {@code spotify.*} configuration tree.
 *
 * @param apiBaseUri base URI of the Spotify Web API (e.g. {@code https://api.spotify.com/v1})
 */
@ConfigurationProperties(prefix = "spotify")
public record SpotifyProperties(
        String apiBaseUri
) {
}
