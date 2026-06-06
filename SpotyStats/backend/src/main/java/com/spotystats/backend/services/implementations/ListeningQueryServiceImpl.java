package com.spotystats.backend.services.implementations;

import com.spotystats.backend.DTOs.listening.ArtistShareResponse;
import com.spotystats.backend.DTOs.listening.PlayedTrackResponse;
import com.spotystats.backend.DTOs.listening.TodayHistoryResponse;
import com.spotystats.backend.DTOs.listening.WeekStatsResponse;
import com.spotystats.backend.entities.Play;
import com.spotystats.backend.entities.Track;
import com.spotystats.backend.entities.TrackArtist;
import com.spotystats.backend.repositories.PlayRepository;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import com.spotystats.backend.services.interfaces.ListeningQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ListeningQueryServiceImpl implements ListeningQueryService {

    private static final int WINDOW_DAYS = 7;

    private final PlayRepository playRepository;

    private final LikedTracksService likedTracksService;

    /**
     * Today's plays in the caller's time zone, newest first, with liked state
     * resolved from the user's Spotify library. Deliberately not transactional:
     * the history query fetch-joins everything the mapping touches, and the liked
     * lookup is a Spotify HTTP call that must not hold a DB connection hostage.
     */
    @Override
    public TodayHistoryResponse todayHistory(String userId, ZoneId zone) {
        final LocalDate today = LocalDate.now(zone);
        final Instant startOfDay = today.atStartOfDay(zone).toInstant();
        final Instant startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();

        final List<Play> plays = playRepository.findHistoryWindow(userId, startOfDay, startOfTomorrow);
        final Map<String, Boolean> likedByTrackId = likedStatusesOrEmpty(distinctTrackIds(plays));

        final List<PlayedTrackResponse> tracks = plays
                .stream()
                .map(play -> toPlayedTrack(play, likedByTrackId))
                .toList();

        return new TodayHistoryResponse(today, tracks);
    }

    /**
     * Aggregates the rolling last-7-days window; the play-count delta compares it
     * against the 7 days before that.
     */
    @Override
    @Transactional(readOnly = true)
    public WeekStatsResponse weekStats(String userId) {
        final Instant now = Instant.now();
        final Instant weekAgo = now.minus(WINDOW_DAYS, ChronoUnit.DAYS);
        final Instant twoWeeksAgo = weekAgo.minus(WINDOW_DAYS, ChronoUnit.DAYS);

        final long tracksPlayed = playRepository.countPlaysInWindow(userId, weekAgo, now);
        final long tracksPlayedPriorWeek = playRepository.countPlaysInWindow(userId, twoWeeksAgo, weekAgo);

        return WeekStatsResponse
                .builder()
                .tracksPlayed(tracksPlayed)
                .tracksPlayedDeltaPercent(deltaPercent(tracksPlayed, tracksPlayedPriorWeek))
                .listeningTimeMs(playRepository.sumListeningTimeMsInWindow(userId, weekAgo, now))
                .uniqueArtists(playRepository.countUniqueArtistsInWindow(userId, weekAgo, now))
                .newArtists(playRepository.countNewArtistsSince(userId, weekAgo))
                .uniqueTracks(playRepository.countUniqueTracksInWindow(userId, weekAgo, now))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtistShareResponse> artistBreakdown(String userId) {
        final Instant weekAgo = Instant
                .now()
                .minus(WINDOW_DAYS, ChronoUnit.DAYS);

        return playRepository
                .aggregateArtistSharesSince(userId, weekAgo)
                .stream()
                .map(share -> new ArtistShareResponse(
                        share.getArtistName(),
                        share.getTrackCount(),
                        share.getListeningTimeMs()))
                .toList();
    }

    /**
     * Liked state is decoration on the history panel — if Spotify rejects the
     * lookup (e.g. missing library scope), show the history anyway with all
     * hearts unliked rather than failing the whole request.
     */
    private Map<String, Boolean> likedStatusesOrEmpty(List<String> trackIds) {
        try {
            return likedTracksService.likedStatuses(trackIds);
        } catch (RestClientResponseException ex) {
            log.warn("Liked-status lookup failed with {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Map.of();
        }
    }

    private static List<String> distinctTrackIds(List<Play> plays) {
        return plays
                .stream()
                .map(play -> play.getTrack().getSpotifyId())
                .distinct()
                .toList();
    }

    private static PlayedTrackResponse toPlayedTrack(Play play, Map<String, Boolean> likedByTrackId) {
        final Track track = play.getTrack();

        return PlayedTrackResponse
                .builder()
                .id(String.valueOf(play.getId()))
                .trackId(track.getSpotifyId())
                .title(track.getName())
                .artist(creditedArtists(track))
                .album(track.getAlbum() != null ? track.getAlbum().getName() : "")
                .albumArtUrl(track.getAlbum() != null ? track.getAlbum().getCoverUrl() : null)
                .playedAt(play.getPlayedAt())
                .durationMs(track.getDurationMs())
                .liked(likedByTrackId.getOrDefault(track.getSpotifyId(), false))
                .build();
    }

    /**
     * Joins the track's credited artists in position order. {@code distinct()}
     * guards against Hibernate bag duplication: when several plays of the same
     * track are fetch-joined in one query, the track's credits list is
     * re-initialised per occurrence and every credit repeats.
     */
    private static String creditedArtists(Track track) {
        if (track.getCredits().isEmpty()) {
            return track.getPrimaryArtist() != null ? track.getPrimaryArtist().getName() : "";
        }

        return track
                .getCredits()
                .stream()
                .distinct()
                .sorted(Comparator.comparing(TrackArtist::getPosition))
                .map(credit -> credit.getArtist().getName())
                .collect(Collectors.joining(", "));
    }

    /**
     * Whole-percent change vs the prior window, or null when the prior window is
     * empty (a percentage of zero is meaningless).
     */
    private static Integer deltaPercent(long current, long prior) {
        if (prior == 0) {
            return null;
        }

        return Math.toIntExact(Math.round((current - prior) * 100.0 / prior));
    }
}
