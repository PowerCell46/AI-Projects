package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.DTOs.listening.ArtistShareResponse;
import com.spotystats.backend.DTOs.listening.TodayHistoryResponse;
import com.spotystats.backend.DTOs.listening.WeekStatsResponse;

import java.time.ZoneId;
import java.util.List;


public interface ListeningQueryService {

    TodayHistoryResponse todayHistory(String userId, ZoneId zone);

    WeekStatsResponse weekStats(String userId);

    List<ArtistShareResponse> artistBreakdown(String userId);
}
