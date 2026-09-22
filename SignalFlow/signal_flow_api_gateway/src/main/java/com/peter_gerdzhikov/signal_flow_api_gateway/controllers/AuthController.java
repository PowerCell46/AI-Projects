package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

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
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.UserResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.AuthService;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.CookieFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final TokenService tokenService;

    private final CookieFactory cookieFactory;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        log.info("Received registration request.");
        User user = authService.register(request);
        String token = tokenService.mint(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.issue(token).toString())
                .body(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        log.info("Received login request.");
        User user = authService.login(request);
        String token = tokenService.mint(user);

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.issue(token).toString())
                .body(toResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.info("Received logout request.");
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(@AuthenticationPrincipal Jwt jwt) {
        log.info("Received /me request.");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(toResponse(jwt));
    }

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private UserResponseDTO toResponse(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        Role role = Role.valueOf(jwt.getClaimAsString("role"));
        return new UserResponseDTO(id, jwt.getClaimAsString("email"), role, jwt.getClaimAsInstant("createdAt"));
    }
}
