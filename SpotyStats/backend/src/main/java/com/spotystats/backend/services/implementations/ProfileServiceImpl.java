package com.spotystats.backend.services.implementations;

import com.spotystats.backend.DTOs.profile.ProfileResponse;
import com.spotystats.backend.DTOs.spotify.SpotifyUserProfile;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.services.interfaces.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final SpotifyApiClient spotifyApiClient;

    @Override
    public ProfileResponse currentProfile() {
        final SpotifyUserProfile profile = spotifyApiClient.get("/me", SpotifyUserProfile.class);

        return new ProfileResponse(
                profile.id(),
                profile.displayName(),
                profile.email(),
                firstImageUrl(profile));
    }

    private static String firstImageUrl(SpotifyUserProfile profile) {
        if (profile.images() == null || profile.images().isEmpty()) {
            return null;
        }

        return profile.images().getFirst().url();
    }
}
