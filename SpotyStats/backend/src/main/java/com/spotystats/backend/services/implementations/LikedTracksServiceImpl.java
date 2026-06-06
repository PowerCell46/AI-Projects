package com.spotystats.backend.services.implementations;

import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Bridges to the user's Spotify "Liked Songs" library. Liked state lives in
 * Spotify, not in our database, so both reads and writes go straight upstream.
 *
 * <p>Uses the unified library endpoints ({@code /me/library}) introduced by
 * Spotify's February 2026 API changes — the older type-specific endpoints
 * ({@code /me/tracks/contains}, {@code PUT /me/tracks}) now answer with a bare
 * 403 for apps without legacy access.
 */
@Service
@RequiredArgsConstructor
public class LikedTracksServiceImpl implements LikedTracksService {

    private static final int CONTAINS_BATCH_SIZE = 40;

    private static final String TRACK_URI_PREFIX = "spotify:track:";

    private final SpotifyApiClient spotifyApiClient;

    /**
     * Checks which of the given tracks are saved in the user's library, batching
     * requests at the library endpoint's 40-item limit.
     */
    @Override
    public Map<String, Boolean> likedStatuses(List<String> trackIds) {
        final Map<String, Boolean> statuses = new HashMap<>();

        for (int offset = 0; offset < trackIds.size(); offset += CONTAINS_BATCH_SIZE) {
            final List<String> batch = trackIds.subList(
                    offset,
                    Math.min(offset + CONTAINS_BATCH_SIZE, trackIds.size()));

            final boolean[] containsFlags = spotifyApiClient
                    .get("/me/library/contains?uris=" + joinAsTrackUris(batch), boolean[].class);

            for (int index = 0; index < batch.size(); index++) {
                statuses.put(batch.get(index), containsFlags[index]);
            }
        }

        return statuses;
    }

    @Override
    public void setLiked(String trackId, boolean liked) {
        final String path = "/me/library?uris=" + TRACK_URI_PREFIX + trackId;

        if (liked) {
            spotifyApiClient.put(path);
        } else {
            spotifyApiClient.delete(path);
        }
    }

    private static String joinAsTrackUris(List<String> trackIds) {
        return trackIds
                .stream()
                .map(trackId -> TRACK_URI_PREFIX + trackId)
                .collect(Collectors.joining(","));
    }
}
