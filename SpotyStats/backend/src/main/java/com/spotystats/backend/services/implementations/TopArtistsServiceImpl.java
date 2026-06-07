package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.listening.ArtistRankingResponse;
import com.spotystats.backend.dtos.listening.RankedArtistResponse;
import com.spotystats.backend.dtos.spotify.SpotifyArtist;
import com.spotystats.backend.dtos.spotify.SpotifyTopArtistsPage;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.services.interfaces.FollowedArtistsService;
import com.spotystats.backend.services.interfaces.TopArtistsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


/**
 * The user's most-listened artists as computed by Spotify over their whole
 * account history. Spotify shares only the affinity-ordered list — no play
 * counts or listening time — but it reaches back years further than the plays
 * we capture ourselves.
 */
@Service
@RequiredArgsConstructor
public class TopArtistsServiceImpl implements TopArtistsService {

    private static final String LONG_TERM_TOP_ARTISTS_PATH = "/me/top/artists?time_range=long_term&limit=50";

    private final SpotifyApiClient spotifyApiClient;

    private final FollowedArtistsService followedArtistsService;

    @Override
    public ArtistRankingResponse longTermTopArtists() {
        final SpotifyTopArtistsPage page = spotifyApiClient
                .get(LONG_TERM_TOP_ARTISTS_PATH, SpotifyTopArtistsPage.class);

        if (page == null || page.getItems() == null) {
            return new ArtistRankingResponse(null, List.of());
        }

        final List<SpotifyArtist> artists = page
                .getItems()
                .stream()
                .filter(artist -> artist.getId() != null)
                .toList();

        final Map<String, Boolean> followedByArtistId = followedArtistsService.followedStatuses(
                artists
                        .stream()
                        .map(SpotifyArtist::getId)
                        .toList());

        return new ArtistRankingResponse(
                null,
                artists
                        .stream()
                        .map(artist -> toRank(artist, followedByArtistId))
                        .toList());
    }

    private static RankedArtistResponse toRank(SpotifyArtist artist, Map<String, Boolean> followedByArtistId) {
        return RankedArtistResponse
                .builder()
                .artistId(artist.getId())
                .artistName(artist.getName())
                .imageUrl(firstImageUrl(artist))
                .followed(followedByArtistId.getOrDefault(artist.getId(), false))
                .build();
    }

    private static String firstImageUrl(SpotifyArtist artist) {
        if (artist.getImages() == null || artist.getImages().isEmpty()) {
            return null;
        }

        return artist
                .getImages()
                .getFirst()
                .getUrl();
    }
}
