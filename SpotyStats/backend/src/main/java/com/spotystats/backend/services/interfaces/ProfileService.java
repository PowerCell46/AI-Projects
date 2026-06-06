package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.DTOs.profile.ProfileResponse;


public interface ProfileService {

    /**
     * Fetches the current user's profile live from Spotify (exercising the BFF token
     * proxy + automatic refresh) and maps it to the SPA-facing shape.
     */
    ProfileResponse currentProfile();
}
