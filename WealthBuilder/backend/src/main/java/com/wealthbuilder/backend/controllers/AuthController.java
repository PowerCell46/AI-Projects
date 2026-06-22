package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.auth.AuthenticatedUser;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;
import com.wealthbuilder.backend.config.AuthTokenCookie;
import com.wealthbuilder.backend.services.interfaces.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * Public auth endpoints (register, login, logout) plus the authenticated current-user lookup.
 * Register and login drop the JWT into an httpOnly cookie and return only the user snapshot, so
 * the token never reaches JavaScript; logout clears that cookie.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final AuthTokenCookie authTokenCookie;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return establishSession(authService.register(request), response);
    }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return establishSession(authService.login(request), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null) {
            authService.logout(authentication.getName());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, authTokenCookie.clear().toString());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    private CurrentUserResponse establishSession(AuthenticatedUser authenticated, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authTokenCookie.issue(authenticated.getToken()).toString());

        return authenticated.getUser();
    }
}
