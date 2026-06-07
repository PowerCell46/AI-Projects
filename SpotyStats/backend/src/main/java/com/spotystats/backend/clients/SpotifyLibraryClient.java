package com.spotystats.backend.clients;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Shared access to the user's unified Spotify library ({@code /me/library}) —
 * the endpoints introduced by Spotify's February 2026 API changes that save,
 * remove and contains-check any content type via Spotify URIs. Callers supply
 * the URI prefix for their entity type (e.g. {@code spotify:track:},
 * {@code spotify:artist:}).
 */
@Component
@RequiredArgsConstructor
public class SpotifyLibraryClient {

    private static final int CONTAINS_BATCH_SIZE = 40;

    private final SpotifyApiClient spotifyApiClient;

    /**
     * Checks which of the given ids are in the user's library, batching requests
     * at the contains endpoint's 40-item limit. Result keys are the plain ids.
     */
    public Map<String, Boolean> containsStatuses(String uriPrefix, List<String> ids) {
        final Map<String, Boolean> statuses = new HashMap<>();

        for (int offset = 0; offset < ids.size(); offset += CONTAINS_BATCH_SIZE) {
            final List<String> batch = ids.subList(
                    offset,
                    Math.min(offset + CONTAINS_BATCH_SIZE, ids.size()));

            final boolean[] containsFlags = spotifyApiClient
                    .get("/me/library/contains?uris=" + joinAsUris(uriPrefix, batch), boolean[].class);

            for (int index = 0; index < batch.size(); index++) {
                statuses.put(batch.get(index), containsFlags[index]);
            }
        }

        return statuses;
    }

    /**
     * Saves a single entity to the user's library, or removes it.
     */
    public void setSaved(String uriPrefix, String id, boolean saved) {
        final String path = "/me/library?uris=" + uriPrefix + id;

        if (saved) {
            spotifyApiClient.put(path);

        } else {
            spotifyApiClient.delete(path);
        }
    }

    private static String joinAsUris(String uriPrefix, List<String> ids) {
        return ids
                .stream()
                .map(id -> uriPrefix + id)
                .collect(Collectors.joining(","));
    }
}
