package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.dtos.auth.AuthResponse;
import com.wealthbuilder.backend.dtos.auth.CurrentUserResponse;
import com.wealthbuilder.backend.dtos.auth.LoginRequest;
import com.wealthbuilder.backend.dtos.auth.RegisterRequest;
import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.exceptions.UsernameAlreadyTakenException;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import com.wealthbuilder.backend.services.interfaces.AuthService;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


/**
 * Default {@link AuthService}. Tokens are minted here so both registration and login hand
 * the SPA a ready-to-use bearer token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final HoldingRepository holdingRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    /**
     * Ensures the username is free, then inserts the {@link Role#USER}. The duplicate check
     * and insert share one transaction so concurrent registrations can't both slip through.
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyTakenException(request.getUsername());
        }

        final User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                Role.USER);
        userRepository.save(user);

        log.info("Registered new user '{}'", user.getUsername());

        return issueTokenFor(user);
    }

    /**
     * Delegates the BCrypt comparison to the authentication manager; bad credentials raise
     * {@code BadCredentialsException}, surfaced as 401.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        final User user = requireUser(request.getUsername());

        log.info("User '{}' logged in", user.getUsername());

        return issueTokenFor(user);
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse me(String username) {
        final User user = requireUser(username);
        log.debug("Resolved current user '{}'", username);

        return CurrentUserResponse.of(user.getUsername(), user.getRole(), computeBalance(user));
    }

    private AuthResponse issueTokenFor(User user) {
        final String token = jwtService.issueToken(user.getUsername(), user.getRole());

        return AuthResponse.of(token);
    }

    private User requireUser(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }

    /**
     * Net invested amount = sum of every holding's {@code boughtForAmount} across all of the
     * user's assets. Returns zero when the user holds nothing yet.
     */
    private BigDecimal computeBalance(User user) {
        return holdingRepository.sumInvestedByUser(user);
    }
}
