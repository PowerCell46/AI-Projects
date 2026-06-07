package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.dtos.listening.ArtistRankingResponse;
import com.spotystats.backend.dtos.listening.ArtistShareResponse;
import com.spotystats.backend.dtos.listening.DailyHistoryResponse;
import com.spotystats.backend.dtos.listening.HistoryPageResponse;
import com.spotystats.backend.dtos.listening.PeriodStatsResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


public interface ListeningQueryService {

    DailyHistoryResponse todayHistory(String userId, ZoneId zone);

    HistoryPageResponse historyPage(String userId, ZoneId zone, LocalDate before);

    PeriodStatsResponse periodStats(String userId, Instant since, Instant priorSince);

    List<ArtistShareResponse> artistBreakdown(String userId, Instant since);

    ArtistRankingResponse artistRanking(String userId, Instant since);
}
