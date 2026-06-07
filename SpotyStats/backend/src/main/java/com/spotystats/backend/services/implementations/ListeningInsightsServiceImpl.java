package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.listening.InsightsResponse;
import com.spotystats.backend.dtos.listening.InsightsResponse.HourlyActivity;
import com.spotystats.backend.dtos.listening.InsightsResponse.TopTrack;
import com.spotystats.backend.dtos.listening.InsightsResponse.WeekdayActivity;
import com.spotystats.backend.dtos.listening.InsightsResponse.WeeklyTrendPoint;
import com.spotystats.backend.repositories.PlayRepository;
import com.spotystats.backend.services.interfaces.ListeningInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;


/**
 * Aggregated listening patterns. Hourly/weekday/top-track aggregates span all
 * recorded plays; the weekly trend covers the recent weeks only.
 */
@Service
@RequiredArgsConstructor
public class ListeningInsightsServiceImpl implements ListeningInsightsService {

    private static final int TREND_WEEKS = 8;

    private static final int TOP_TRACKS_LIMIT = 10;

    private final PlayRepository playRepository;

    @Override
    @Transactional(readOnly = true)
    public InsightsResponse insights(String userId, ZoneId zone) {
        final String zoneName = zone.getId();

        return InsightsResponse
                .builder()
                .hourlyActivity(hourlyActivity(userId, zoneName))
                .weekdayActivity(weekdayActivity(userId, zoneName))
                .weeklyTrend(weeklyTrend(userId, zoneName))
                .topTracks(topTracks(userId))
                .build();
    }

    private List<HourlyActivity> hourlyActivity(String userId, String zoneName) {
        return playRepository
                .aggregateHourlyActivity(userId, zoneName)
                .stream()
                .map(view -> new HourlyActivity(view.getHour(), view.getPlays()))
                .toList();
    }

    private List<WeekdayActivity> weekdayActivity(String userId, String zoneName) {
        return playRepository
                .aggregateWeekdayActivity(userId, zoneName)
                .stream()
                .map(view -> new WeekdayActivity(
                        view.getIsoWeekday(),
                        view.getPlays(),
                        view.getListeningTimeMs()))
                .toList();
    }

    private List<WeeklyTrendPoint> weeklyTrend(String userId, String zoneName) {
        final Instant trendStart = Instant
                .now()
                .minus(TREND_WEEKS * 7L, ChronoUnit.DAYS);

        return playRepository
                .aggregateWeeklyTrend(userId, zoneName, trendStart)
                .stream()
                .map(view -> new WeeklyTrendPoint(
                        view.getWeekStart(),
                        view.getPlays(),
                        view.getListeningTimeMs()))
                .toList();
    }

    private List<TopTrack> topTracks(String userId) {
        return playRepository
                .rankTopTracks(userId, PageRequest.of(0, TOP_TRACKS_LIMIT))
                .stream()
                .map(view -> TopTrack
                        .builder()
                        .trackId(view.getTrackId())
                        .title(view.getTitle())
                        .artist(view.getArtistName())
                        .albumArtUrl(view.getAlbumArtUrl())
                        .playCount(view.getPlayCount())
                        .listeningTimeMs(view.getListeningTimeMs())
                        .build())
                .toList();
    }
}
