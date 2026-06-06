package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.entities.AppUser;
import org.springframework.security.oauth2.core.user.OAuth2User;


public interface AppUserService {

    /**
     * Creates or updates the local {@link AppUser} from the attributes Spotify returned
     * for the authenticated user. Called on every successful login so profile fields stay fresh.
     */
    AppUser upsertFromSpotifyUser(OAuth2User spotifyUser);
}
