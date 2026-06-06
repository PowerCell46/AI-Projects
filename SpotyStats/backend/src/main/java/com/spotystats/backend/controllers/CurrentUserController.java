package com.spotystats.backend.controllers;

import com.spotystats.backend.DTOs.auth.CurrentUserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class CurrentUserController {

    /**
     * Returns the current authentication state. Permitted for anonymous callers so the
     * SPA can probe login status; returns {@code authenticated=false} when not logged in.
     */
    @GetMapping("/api/me")
    public CurrentUserResponse me(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OAuth2User spotifyUser) {

            return CurrentUserResponse.authenticated(token.getName(), spotifyUser.getAttribute("display_name"));
        }

        return CurrentUserResponse.anonymous();
    }
}
