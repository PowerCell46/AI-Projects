package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.listening.LikedPageResponse;
import com.spotystats.backend.dtos.listening.LikedTrackResponse;
import com.spotystats.backend.dtos.spotify.SpotifyArtist;
import com.spotystats.backend.dtos.spotify.SpotifySavedTracksPage;
import com.spotystats.backend.dtos.spotify.SpotifySavedTracksPage.SavedTrackItem;
import com.spotystats.backend.dtos.spotify.SpotifyTrack;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.clients.SpotifyLibraryClient;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedTracksServiceImpl implements LikedTracksService {

    private static final String TRACK_URI_PREFIX = "spotify:track:";

    private final SpotifyApiClient spotifyApiClient;

    private final SpotifyLibraryClient spotifyLibraryClient;

    @Override
    public Map<String, Boolean> likedStatuses(List<String> trackIds) {
        try {
            return spotifyLibraryClient.containsStatuses(TRACK_URI_PREFIX, trackIds);
        } catch (RestClientResponseException ex) {
            log.warn("Liked-status lookup failed with {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Map.of();
        }
    }

    @Override
    public void setLiked(String trackId, boolean liked) {
        spotifyLibraryClient.setSaved(TRACK_URI_PREFIX, trackId, liked);
    }

    /**
     * One page of the user's Liked Songs, straight from Spotify (most recently
     * added first). {@code GET /me/tracks} itself survived the February 2026
     * deprecations — only the type-specific contains/save/remove variants died.
     */
    @Override
    public LikedPageResponse likedPage(int limit, int offset) {
        final SpotifySavedTracksPage page = spotifyApiClient
                .get("/me/tracks?limit=" + limit + "&offset=" + offset, SpotifySavedTracksPage.class);

        if (page == null || page.getItems() == null) {
            return new LikedPageResponse(0, limit, offset, List.of());
        }

        final List<LikedTrackResponse> items = page
                .getItems()
                .stream()
                .filter(item -> item.getTrack() != null && item.getTrack().getId() != null)
                .map(LikedTracksServiceImpl::toLikedTrack)
                .toList();

        return new LikedPageResponse(
                page.getTotal() != null ? page.getTotal() : items.size(),
                limit,
                offset,
                items);
    }

    private static LikedTrackResponse toLikedTrack(SavedTrackItem item) {
        final SpotifyTrack track = item.getTrack();

        return LikedTrackResponse
                .builder()
                .trackId(track.getId())
                .title(track.getName())
                .artist(joinArtistNames(track.getArtists()))
                .album(track.getAlbum() != null ? track.getAlbum().getName() : "")
                .albumArtUrl(firstImageUrl(track))
                .addedAt(item.getAddedAt())
                .durationMs(track.getDurationMs() != null ? track.getDurationMs() : 0)
                .build();
    }

    private static String joinArtistNames(List<SpotifyArtist> artists) {
        if (artists == null || artists.isEmpty()) {
            return "";
        }

        return artists
                .stream()
                .map(SpotifyArtist::getName)
                .collect(Collectors.joining(", "));
    }

    private static String firstImageUrl(SpotifyTrack track) {
        if (track.getAlbum() == null
                || track.getAlbum().getImages() == null
                || track.getAlbum().getImages().isEmpty()) {
            return null;
        }

        return track
                .getAlbum()
                .getImages()
                .getFirst()
                .getUrl();
    }
}
