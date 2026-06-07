package com.spotystats.backend.handlers;

import com.spotystats.backend.configurations.AppProperties;
import com.spotystats.backend.services.interfaces.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;


/**
 * On successful Spotify login, refreshes the local user profile and redirects
 * the browser to the SPA's overview page — the listening dashboard is the
 * app's home, not the account details.
 */
@Slf4j
@Component
public class SpotifyLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserService appUserService;

    private final String overviewRedirectUri;

    public SpotifyLoginSuccessHandler(AppUserService appUserService, AppProperties appProperties) {
        this.appUserService = appUserService;
        this.overviewRedirectUri = appProperties.frontendBaseUri() + "/overview";
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OAuth2User spotifyUser) {

            appUserService.upsertFromSpotifyUser(spotifyUser);
            log.info("Spotify login succeeded for user {}", token.getName());
        }

        response.sendRedirect(overviewRedirectUri);
    }
}
