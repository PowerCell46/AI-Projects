package com.spotystats.backend.services.implementations;

import com.spotystats.backend.clients.SpotifyLibraryClient;
import com.spotystats.backend.services.interfaces.FollowedArtistsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;


/**
 * Bridges to the user's followed artists on Spotify. Follow state lives in
 * Spotify, not in our database, so both reads and writes go straight upstream
 * through the unified library endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowedArtistsServiceImpl implements FollowedArtistsService {

    private static final String ARTIST_URI_PREFIX = "spotify:artist:";

    private final SpotifyLibraryClient spotifyLibraryClient;

    @Override
    public Map<String, Boolean> followedStatuses(List<String> artistIds) {
        try {
            return spotifyLibraryClient.containsStatuses(ARTIST_URI_PREFIX, artistIds);
        } catch (RestClientResponseException ex) {
            log.warn("Followed-status lookup failed with {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Map.of();
        }
    }

    @Override
    public void setFollowed(String artistId, boolean followed) {
        spotifyLibraryClient.setSaved(ARTIST_URI_PREFIX, artistId, followed);
    }
}
