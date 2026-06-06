package com.spotystats.backend.controllers;

import com.spotystats.backend.DTOs.listening.ArtistShareResponse;
import com.spotystats.backend.DTOs.listening.LikedUpdateRequest;
import com.spotystats.backend.DTOs.listening.TodayHistoryResponse;
import com.spotystats.backend.DTOs.listening.WeekStatsResponse;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import com.spotystats.backend.services.interfaces.ListeningQueryService;
import com.spotystats.backend.services.interfaces.ListeningSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.time.ZoneId;
import java.util.List;


@RestController
@RequestMapping("/api/listening")
@RequiredArgsConstructor
public class ListeningController {

    private final ListeningSyncService listeningSyncService;

    private final ListeningQueryService listeningQueryService;

    private final LikedTracksService likedTracksService;

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync(Authentication authentication) {
        listeningSyncService.syncRecentlyPlayed(authentication.getName());
    }

    @GetMapping("/today")
    public TodayHistoryResponse today(
            Authentication authentication,
            @RequestParam(defaultValue = "UTC") String zone) {

        return listeningQueryService.todayHistory(authentication.getName(), parseZoneOrUtc(zone));
    }

    @GetMapping("/week-stats")
    public WeekStatsResponse weekStats(Authentication authentication) {
        return listeningQueryService.weekStats(authentication.getName());
    }

    @GetMapping("/artist-breakdown")
    public List<ArtistShareResponse> artistBreakdown(Authentication authentication) {
        return listeningQueryService.artistBreakdown(authentication.getName());
    }

    @PostMapping("/tracks/{trackId}/liked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLiked(
            @PathVariable String trackId,
            @Valid @RequestBody LikedUpdateRequest request) {

        likedTracksService.setLiked(trackId, request.getLiked());
    }

    private static ZoneId parseZoneOrUtc(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (Exception invalidZone) {
            return ZoneId.of("UTC");
        }
    }
}
