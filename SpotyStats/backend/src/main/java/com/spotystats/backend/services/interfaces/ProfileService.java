package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.dtos.profile.ProfileResponse;

import java.time.ZoneId;


public interface ProfileService {

    /**
     * Fetches the current user's profile live from Spotify (exercising the BFF token
     * proxy + automatic refresh), enriched with SpotyStats' listening totals.
     */
    ProfileResponse currentProfile(String userId, ZoneId zone);
}
