package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.listening.ArtistRankingResponse;
import com.spotystats.backend.dtos.listening.ArtistShareResponse;
import com.spotystats.backend.dtos.listening.DailyHistoryResponse;
import com.spotystats.backend.dtos.listening.HistoryPageResponse;
import com.spotystats.backend.dtos.listening.PeriodStatsResponse;
import com.spotystats.backend.dtos.listening.PlayedTrackResponse;
import com.spotystats.backend.dtos.listening.RankedArtistResponse;
import com.spotystats.backend.entities.Play;
import com.spotystats.backend.entities.Track;
import com.spotystats.backend.entities.TrackArtist;
import com.spotystats.backend.repositories.PlayRepository;
import com.spotystats.backend.repositories.projections.ArtistRankView;
import com.spotystats.backend.services.interfaces.ArtistImageService;
import com.spotystats.backend.services.interfaces.FollowedArtistsService;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import com.spotystats.backend.services.interfaces.ListeningQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ListeningQueryServiceImpl implements ListeningQueryService {

    private static final int HISTORY_PAGE_DAYS = 7;

    private static final int ARTIST_RANKING_LIMIT = 50;

    private final PlayRepository playRepository;

    private final LikedTracksService likedTracksService;

    private final FollowedArtistsService followedArtistsService;

    private final ArtistImageService artistImageService;

    /**
     * Today's plays in the caller's time zone, newest first. Always reports
     * today's date, even when nothing has been played yet. Deliberately not
     * transactional — the liked-status decoration calls Spotify mid-flow.
     */
    @Override
    public DailyHistoryResponse todayHistory(String userId, ZoneId zone) {
        final LocalDate today = LocalDate.now(zone);
        final List<DailyHistoryResponse> days = historyDays(userId, zone, today, today.plusDays(1));

        return days.isEmpty()
                ? new DailyHistoryResponse(today, List.of())
                : days.getFirst();
    }

    /**
     * One diary page: the week of days ending just before {@code before}
     * (defaults to "now"), with only play-bearing days included. The returned
     * cursor already skips past any play-free gap, so paging never serves
     * empty weeks. Deliberately not transactional — the liked-status decoration
     * calls Spotify mid-flow.
     */
    @Override
    public HistoryPageResponse historyPage(String userId, ZoneId zone, LocalDate before) {
        final LocalDate end = before != null ? before : LocalDate.now(zone).plusDays(1);
        final LocalDate start = end.minusDays(HISTORY_PAGE_DAYS);

        final List<DailyHistoryResponse> days = historyDays(userId, zone, start, end);

        return new HistoryPageResponse(days, nextBefore(userId, zone, start));
    }

    /**
     * Aggregates the window from {@code since} to now. The play-count delta
     * compares against the equally long window starting at {@code priorSince} —
     * yesterday up to the same time of day, or the week before the rolling
     * week — so a partial period is never measured against a full one.
     */
    @Override
    @Transactional(readOnly = true)
    public PeriodStatsResponse periodStats(String userId, Instant since, Instant priorSince) {
        final Instant now = Instant.now();
        final Instant priorEnd = priorSince.plus(Duration.between(since, now));

        final long tracksPlayed = playRepository.countPlaysInWindow(userId, since, now);
        final long tracksPlayedPrior = playRepository.countPlaysInWindow(userId, priorSince, priorEnd);

        return PeriodStatsResponse
                .builder()
                .tracksPlayed(tracksPlayed)
                .tracksPlayedDeltaPercent(deltaPercent(tracksPlayed, tracksPlayedPrior))
                .listeningTimeMs(playRepository.sumListeningTimeMsInWindow(userId, since, now))
                .uniqueArtists(playRepository.countUniqueArtistsInWindow(userId, since, now))
                .newArtists(playRepository.countNewArtistsSince(userId, since))
                .uniqueTracks(playRepository.countUniqueTracksInWindow(userId, since, now))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtistShareResponse> artistBreakdown(String userId, Instant since) {
        return playRepository
                .aggregateArtistSharesSince(userId, since)
                .stream()
                .map(share -> new ArtistShareResponse(
                        share.getArtistName(),
                        share.getTrackCount(),
                        share.getListeningTimeMs()))
                .toList();
    }

    /**
     * Ranks artists by plays, with portraits resolved through the image cache
     * and follow state looked up on Spotify. Not transactional: both
     * enrichments may call Spotify, which must not hold a DB connection.
     */
    @Override
    public ArtistRankingResponse artistRanking(String userId, Instant since) {
        final List<ArtistRankView> ranks =
                playRepository.rankArtistsSince(userId, since, PageRequest.of(0, ARTIST_RANKING_LIMIT));

        final List<String> artistIds = ranks
                .stream()
                .map(ArtistRankView::getArtistId)
                .toList();

        final Map<String, String> imageUrls = artistImageService.imageUrlsFor(artistIds);
        final Map<String, Boolean> followedByArtistId = followedArtistsService.followedStatuses(artistIds);

        final List<RankedArtistResponse> artists = ranks
                .stream()
                .map(rank -> RankedArtistResponse
                        .builder()
                        .artistId(rank.getArtistId())
                        .artistName(rank.getArtistName())
                        .imageUrl(imageUrls.get(rank.getArtistId()))
                        .playCount(rank.getPlayCount())
                        .listeningTimeMs(rank.getListeningTimeMs())
                        .uniqueTracks(rank.getUniqueTracks())
                        .followed(followedByArtistId.getOrDefault(rank.getArtistId(), false))
                        .build())
                .toList();

        return new ArtistRankingResponse(playRepository.findEarliestPlayedAt(userId), artists);
    }

    /**
     * Loads the plays of [startDate, endDate) and groups them into per-day
     * responses, newest day first. Days without plays are omitted. Deliberately
     * not transactional: the history query fetch-joins everything the mapping
     * touches, and the liked lookup is a Spotify HTTP call that must not hold
     * a DB connection hostage.
     */
    private List<DailyHistoryResponse> historyDays(
            String userId,
            ZoneId zone,
            LocalDate startDate,
            LocalDate endDate) {

        final Instant start = startDate.atStartOfDay(zone).toInstant();
        final Instant end = endDate.atStartOfDay(zone).toInstant();

        final List<Play> plays = playRepository.findHistoryWindow(userId, start, end);
        final Map<String, Boolean> likedByTrackId = likedTracksService.likedStatuses(distinctTrackIds(plays));

        final Map<LocalDate, List<PlayedTrackResponse>> tracksByDate = new LinkedHashMap<>();

        for (final Play play : plays) {
            final LocalDate date = play
                    .getPlayedAt()
                    .atZone(zone)
                    .toLocalDate();

            tracksByDate
                    .computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(toPlayedTrack(play, likedByTrackId));
        }

        return tracksByDate
                .entrySet()
                .stream()
                .map(entry -> new DailyHistoryResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Where the next diary page should end (exclusive): the day after the
     * newest play older than the current window, or null when no older plays
     * exist.
     */
    private LocalDate nextBefore(String userId, ZoneId zone, LocalDate windowStart) {
        final Instant windowStartInstant = windowStart.atStartOfDay(zone).toInstant();
        final Instant latestOlderPlay = playRepository.findLatestPlayedAtBefore(userId, windowStartInstant);

        if (latestOlderPlay == null) {
            return null;
        }

        return latestOlderPlay
                .atZone(zone)
                .toLocalDate()
                .plusDays(1);
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
