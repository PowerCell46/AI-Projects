package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.auth.AuthResponse;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;
import com.wealthbuilder.backend.services.interfaces.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * Public auth endpoints (register, login) plus the authenticated current-user lookup.
 * Thin by design: validate, delegate to {@link AuthService}, return the DTO.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }
}
