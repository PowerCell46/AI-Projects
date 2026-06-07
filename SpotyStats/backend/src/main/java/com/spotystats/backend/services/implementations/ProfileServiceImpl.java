package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.profile.ProfileResponse;
import com.spotystats.backend.dtos.profile.ProfileResponse.ListeningTotals;
import com.spotystats.backend.dtos.spotify.SpotifyUserProfile;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.repositories.PlayRepository;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import com.spotystats.backend.services.interfaces.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final SpotifyApiClient spotifyApiClient;

    private final PlayRepository playRepository;

    private final LikedTracksService likedTracksService;

    /**
     * Deliberately not transactional — the profile and liked-total lookups call
     * Spotify, and the DB aggregates are independent single queries that need
     * no shared snapshot.
     */
    @Override
    public ProfileResponse currentProfile(String userId, ZoneId zone) {
        final SpotifyUserProfile profile = spotifyApiClient.get("/me", SpotifyUserProfile.class);

        return ProfileResponse
                .builder()
                .spotifyUserId(profile.getId())
                .displayName(profile.getDisplayName())
                .email(profile.getEmail())
                .imageUrl(firstImageUrl(profile))
                .country(profile.getCountry())
                .product(profile.getProduct())
                .followers(profile.getFollowers() != null ? profile.getFollowers().getTotal() : null)
                .totals(listeningTotals(userId, zone))
                .build();
    }

    private ListeningTotals listeningTotals(String userId, ZoneId zone) {
        final Instant now = Instant.now();
        final Instant earliestPlay = playRepository.findEarliestPlayedAt(userId);

        return ListeningTotals
                .builder()
                .totalPlays(playRepository.countPlaysInWindow(userId, Instant.EPOCH, now))
                .totalListeningTimeMs(playRepository.sumListeningTimeMsInWindow(userId, Instant.EPOCH, now))
                .uniqueArtists(playRepository.countUniqueArtistsInWindow(userId, Instant.EPOCH, now))
                .uniqueTracks(playRepository.countUniqueTracksInWindow(userId, Instant.EPOCH, now))
                .trackingSince(toLocalDate(earliestPlay, zone))
                .likedTotal(likedTotalOrNull())
                .build();
    }

    /**
     * The liked count is decoration — a failing Spotify call must not take the
     * whole profile down with it.
     */
    private Long likedTotalOrNull() {
        try {
            return likedTracksService
                    .likedPage(1, 0)
                    .getTotal();
        } catch (RestClientResponseException ex) {
            log.warn("Liked-total lookup failed with {}", ex.getStatusCode());
            return null;
        }
    }

    private static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        if (instant == null) {
            return null;
        }

        return instant
                .atZone(zone)
                .toLocalDate();
    }

    private static String firstImageUrl(SpotifyUserProfile profile) {
        if (profile.getImages() == null || profile.getImages().isEmpty()) {
            return null;
        }

        return profile.getImages().getFirst().getUrl();
    }
}
