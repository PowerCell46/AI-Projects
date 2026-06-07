package com.spotystats.backend.controllers;

import com.spotystats.backend.dtos.listening.ArtistRankingResponse;
import com.spotystats.backend.dtos.listening.ArtistShareResponse;
import com.spotystats.backend.dtos.listening.DailyHistoryResponse;
import com.spotystats.backend.dtos.listening.FollowedUpdateRequest;
import com.spotystats.backend.dtos.listening.HistoryPageResponse;
import com.spotystats.backend.dtos.listening.InsightsResponse;
import com.spotystats.backend.dtos.listening.LikedUpdateRequest;
import com.spotystats.backend.dtos.listening.PeriodStatsResponse;
import com.spotystats.backend.services.interfaces.FollowedArtistsService;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import com.spotystats.backend.services.interfaces.ListeningInsightsService;
import com.spotystats.backend.services.interfaces.ListeningQueryService;
import com.spotystats.backend.services.interfaces.ListeningSyncService;
import com.spotystats.backend.services.interfaces.TopArtistsService;
import com.spotystats.backend.utilities.ZoneIdParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;


@RestController
@RequestMapping("/api/listening")
@RequiredArgsConstructor
public class ListeningController {

    private final ListeningSyncService listeningSyncService;

    private final ListeningQueryService listeningQueryService;

    private final ListeningInsightsService listeningInsightsService;

    private final LikedTracksService likedTracksService;

    private final FollowedArtistsService followedArtistsService;

    private final TopArtistsService topArtistsService;

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync(Authentication authentication) {
        listeningSyncService.syncRecentlyPlayed(authentication.getName());
    }

    @GetMapping("/today")
    public DailyHistoryResponse today(
            Authentication authentication,
            @RequestParam(defaultValue = "UTC") String zone) {

        return listeningQueryService.todayHistory(authentication.getName(), ZoneIdParser.parseOrUtc(zone));
    }

    @GetMapping("/history")
    public HistoryPageResponse history(
            Authentication authentication,
            @RequestParam(defaultValue = "UTC") String zone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before) {

        return listeningQueryService.historyPage(authentication.getName(), ZoneIdParser.parseOrUtc(zone), before);
    }

    /**
     * Stat-card aggregates scoped to the diary's range: {@code today} (in the
     * caller's zone) or the rolling week, each with a delta against the
     * equally long window before it.
     */
    @GetMapping("/stats")
    public PeriodStatsResponse stats(
            Authentication authentication,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "UTC") String zone) {

        final ZoneId zoneId = ZoneIdParser.parseOrUtc(zone);

        return listeningQueryService.periodStats(
                authentication.getName(),
                windowStart(period, zoneId),
                priorWindowStart(period, zoneId));
    }

    /**
     * Artist shares for the Overview pie chart, scoped to the diary's range:
     * {@code today} (in the caller's zone) or the rolling week.
     */
    @GetMapping("/artist-breakdown")
    public List<ArtistShareResponse> artistBreakdown(
            Authentication authentication,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "UTC") String zone) {

        return listeningQueryService.artistBreakdown(
                authentication.getName(),
                windowStart(period, ZoneIdParser.parseOrUtc(zone)));
    }

    /**
     * Rolling periods rank our own captured plays; {@code all} serves Spotify's
     * long-term top artists instead, which reach back years further than the
     * plays we can capture ourselves.
     */
    @GetMapping("/artists")
    public ArtistRankingResponse artists(
            Authentication authentication,
            @RequestParam(defaultValue = "week") String period) {

        if ("all".equals(period)) {
            return topArtistsService.longTermTopArtists();
        }

        return listeningQueryService.artistRanking(authentication.getName(), periodStart(period));
    }

    @GetMapping("/insights")
    public InsightsResponse insights(
            Authentication authentication,
            @RequestParam(defaultValue = "UTC") String zone) {

        return listeningInsightsService.insights(authentication.getName(), ZoneIdParser.parseOrUtc(zone));
    }

    @PostMapping("/tracks/{trackId}/liked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLiked(
            @PathVariable String trackId,
            @Valid @RequestBody LikedUpdateRequest request) {

        likedTracksService.setLiked(trackId, request.getLiked());
    }

    @PostMapping("/artists/{artistId}/followed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setFollowed(
            @PathVariable String artistId,
            @Valid @RequestBody FollowedUpdateRequest request) {

        followedArtistsService.setFollowed(artistId, request.getFollowed());
    }

    /**
     * Maps a period keyword to the rolling window's start; unknown values fall
     * back to the week window.
     */
    private static Instant periodStart(String period) {
        return "month".equals(period)
                ? Instant.now().minus(30, ChronoUnit.DAYS)
                : Instant.now().minus(7, ChronoUnit.DAYS);
    }

    /**
     * Start of the toggled window: today's midnight in the caller's zone, or
     * the rolling week; unknown values fall back to the week window.
     */
    private static Instant windowStart(String period, ZoneId zone) {
        return "today".equals(period)
                ? LocalDate.now(zone).atStartOfDay(zone).toInstant()
                : Instant.now().minus(7, ChronoUnit.DAYS);
    }

    /**
     * Start of the comparison window one period earlier: yesterday's midnight,
     * or the week before the rolling week.
     */
    private static Instant priorWindowStart(String period, ZoneId zone) {
        return "today".equals(period)
                ? LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
                : Instant.now().minus(14, ChronoUnit.DAYS);
    }
}
