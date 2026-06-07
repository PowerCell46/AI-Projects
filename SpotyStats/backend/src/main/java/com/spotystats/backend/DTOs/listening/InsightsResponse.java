package com.spotystats.backend.dtos.listening;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;


/**
 * Aggregated listening patterns for the Insights page. Hourly and weekday
 * activity cover all recorded plays; the trend covers the recent weeks.
 */
@Value
@Builder
public class InsightsResponse {

    List<HourlyActivity> hourlyActivity;

    List<WeekdayActivity> weekdayActivity;

    List<WeeklyTrendPoint> weeklyTrend;

    List<TopTrack> topTracks;

    @Value
    public static class HourlyActivity {

        int hour;

        long plays;
    }

    @Value
    public static class WeekdayActivity {

        int isoWeekday;

        long plays;

        long listeningTimeMs;
    }

    @Value
    public static class WeeklyTrendPoint {

        LocalDate weekStart;

        long plays;

        long listeningTimeMs;
    }

    @Value
    @Builder
    public static class TopTrack {

        String trackId;

        String title;

        String artist;

        String albumArtUrl;

        long playCount;

        long listeningTimeMs;
    }
}
