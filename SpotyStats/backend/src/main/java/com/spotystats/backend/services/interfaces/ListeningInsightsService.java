package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.dtos.listening.InsightsResponse;

import java.time.ZoneId;


public interface ListeningInsightsService {

    InsightsResponse insights(String userId, ZoneId zone);
}
