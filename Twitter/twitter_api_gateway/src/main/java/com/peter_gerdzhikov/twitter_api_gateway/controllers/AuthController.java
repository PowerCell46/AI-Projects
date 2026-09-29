package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ConfirmRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ResendConfirmationRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.auth.UserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.AuthService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.EmailConfirmationService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final CookieFactory cookieFactory;

    private final TokenService tokenService;

    private final EmailConfirmationService emailConfirmationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO register(@Valid @RequestBody RegisterRequestDTO request) {
        User user = authService.register(request);

        return new UserResponseDTO(user.getId(), user.getUsername(), user.getEmail());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmRequestDTO request) {
        emailConfirmationService.confirm(request.getToken());
    }

    @PostMapping("/confirm/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequestDTO request) {
        emailConfirmationService.resend(request.getEmail());
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        User user = authService.login(request);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.issue(tokenService.mint(user)).toString())
                .body(new UserResponseDTO(user.getId(), user.getUsername(), user.getEmail()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    /**
     * Answers from the token's claims alone; authorization never reads the database.
     */
    @GetMapping("/me")
    public UserResponseDTO me(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponseDTO(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                jwt.getClaimAsString("email")
        );
    }
}
