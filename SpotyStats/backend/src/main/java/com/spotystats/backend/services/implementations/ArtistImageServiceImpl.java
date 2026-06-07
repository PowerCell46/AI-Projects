package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.spotify.SpotifyArtist;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.entities.Artist;
import com.spotystats.backend.repositories.ArtistRepository;
import com.spotystats.backend.services.interfaces.ArtistImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Resolves artist portraits, caching them on the artist row. Spotify's batch
 * {@code GET /artists?ids=} is 403-restricted (February 2026 API changes), so
 * missing images are fetched one-by-one via {@code GET /artists/{id}} —
 * capped per request, so a cold cache fills up over a few requests instead of
 * stalling one behind dozens of sequential HTTP calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistImageServiceImpl implements ArtistImageService {

    private static final int MAX_FETCHES_PER_REQUEST = 10;

    private final SpotifyApiClient spotifyApiClient;

    private final ArtistRepository artistRepository;

    /**
     * Deliberately not transactional — cache misses call Spotify mid-loop, and
     * each fetched image is saved independently so one failure loses nothing.
     */
    @Override
    public Map<String, String> imageUrlsFor(List<String> artistIds) {
        final Map<String, String> imageUrlsByArtistId = new HashMap<>();
        int fetchesLeft = MAX_FETCHES_PER_REQUEST;

        for (final Artist artist : artistRepository.findAllById(artistIds)) {
            if (artist.getImageUrl() == null && fetchesLeft > 0) {
                fetchAndStoreImage(artist);
                fetchesLeft--;
            }

            if (artist.getImageUrl() != null) {
                imageUrlsByArtistId.put(artist.getSpotifyId(), artist.getImageUrl());
            }
        }

        return imageUrlsByArtistId;
    }

    /**
     * Best-effort: an unreachable Spotify or an artist without portraits just
     * leaves the image absent — rankings render an initial avatar instead.
     */
    private void fetchAndStoreImage(Artist artist) {
        try {
            final SpotifyArtist spotifyArtist = spotifyApiClient
                    .get("/artists/" + artist.getSpotifyId(), SpotifyArtist.class);

            final String imageUrl = firstImageUrl(spotifyArtist);

            if (imageUrl != null) {
                artist.setImageUrl(imageUrl);
                artistRepository.save(artist);
            }
        } catch (RestClientResponseException ex) {
            log.warn("Could not fetch image for artist {}: {}", artist.getSpotifyId(), ex.getStatusCode());
        }
    }

    private static String firstImageUrl(SpotifyArtist spotifyArtist) {
        if (spotifyArtist == null
                || spotifyArtist.getImages() == null
                || spotifyArtist.getImages().isEmpty()) {
            return null;
        }

        return spotifyArtist
                .getImages()
                .getFirst()
                .getUrl();
    }
}
