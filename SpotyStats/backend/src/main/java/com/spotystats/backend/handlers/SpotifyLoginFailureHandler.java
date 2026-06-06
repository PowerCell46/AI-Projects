package com.spotystats.backend.handlers;

import com.spotystats.backend.configurations.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * On failed Spotify login (user denied consent, callback error, token exchange failure),
 * redirects to the SPA's error page with a short, non-sensitive reason.
 */
@Slf4j
@Component
public class SpotifyLoginFailureHandler implements AuthenticationFailureHandler {

    private final String errorRedirectBaseUri;

    public SpotifyLoginFailureHandler(AppProperties appProperties) {
        this.errorRedirectBaseUri = appProperties.frontendBaseUri() + "/login-error";
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.warn("Spotify login failed: {}", exception.getMessage());

        final String redirectUri = UriComponentsBuilder
                .fromUriString(errorRedirectBaseUri)
                .queryParam("reason", "login_failed")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}
