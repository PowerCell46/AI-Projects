package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.spotify.SpotifyUserProfile;
import com.spotystats.backend.entities.AppUser;
import com.spotystats.backend.repositories.AppUserRepository;
import com.spotystats.backend.services.interfaces.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;


@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {

    private final AppUserRepository appUserRepository;

    private final ObjectMapper objectMapper;

    /**
     * Upserts the user from the OAuth2 userinfo attributes, bound to the typed
     * {@link SpotifyUserProfile} instead of walking raw attribute maps — if
     * Spotify ever changes the payload shape, binding fails loudly rather than
     * silently dropping fields.
     */
    @Override
    @Transactional
    public AppUser upsertFromSpotifyUser(OAuth2User spotifyUser) {
        final String spotifyUserId = spotifyUser.getName();
        final SpotifyUserProfile profile = objectMapper
                .convertValue(spotifyUser.getAttributes(), SpotifyUserProfile.class);

        final AppUser user = appUserRepository
                .findById(spotifyUserId)
                .orElseGet(() -> new AppUser(spotifyUserId));

        user.setDisplayName(profile.getDisplayName());
        user.setEmail(profile.getEmail());
        user.setImageUrl(firstImageUrl(profile));

        return appUserRepository.save(user);
    }

    private static String firstImageUrl(SpotifyUserProfile profile) {
        if (profile.getImages() == null || profile.getImages().isEmpty()) {
            return null;
        }

        return profile
                .getImages()
                .getFirst()
                .getUrl();
    }
}
