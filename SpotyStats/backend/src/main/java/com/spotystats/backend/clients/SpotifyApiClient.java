package com.spotystats.backend.clients;

import com.spotystats.backend.configurations.SpotifyProperties;
import com.spotystats.backend.exceptions.SpotifyAuthorizationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


/**
 * Thin, authenticated gateway to the Spotify Web API. Resolves the current user's
 * access token (refreshing it transparently when expired) and attaches it as a bearer
 * token. The browser never sees these tokens — all Spotify traffic flows through here.
 */
@Slf4j
@Component
public class SpotifyApiClient {

    private static final String SPOTIFY_REGISTRATION_ID = "spotify";

    private final RestClient restClient;

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public SpotifyApiClient(
            RestClient.Builder restClientBuilder,
            SpotifyProperties spotifyProperties,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        this.restClient = restClientBuilder
                .baseUrl(spotifyProperties.apiBaseUri())
                .build();
        this.authorizedClientManager = authorizedClientManager;
    }

    /**
     * Issues an authenticated GET against the Spotify API and deserialises the JSON body.
     *
     * @param path     path relative to the configured API base URI (e.g. {@code /me})
     * @param bodyType target type for the response body
     */
    public <T> T get(String path, Class<T> bodyType) {
        final String accessToken = currentUserAccessToken();

        return restClient
                .get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(bodyType);
    }

    /**
     * Issues an authenticated, bodiless PUT — used for endpoints that take their
     * arguments as query parameters (e.g. {@code PUT /me/library?uris=...}).
     */
    public void put(String path) {
        final String accessToken = currentUserAccessToken();

        restClient
                .put()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Issues an authenticated DELETE against the Spotify API.
     */
    public void delete(String path) {
        final String accessToken = currentUserAccessToken();

        restClient
                .delete()
                .uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    private String currentUserAccessToken() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        final OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(SPOTIFY_REGISTRATION_ID)
                .principal(authentication)
                .build();

        final OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient == null) {
            throw new SpotifyAuthorizationException("No authorized Spotify client for the current user.");
        }

        return authorizedClient.getAccessToken().getTokenValue();
    }
}
