package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidConfirmationTokenException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.ConfirmationRequestService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.ConfirmationTokenService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.EmailConfirmationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailConfirmationServiceImpl implements EmailConfirmationService {

    private final Clock clock;

    private final Duration resendCooldown;

    private final UserRepository userRepository;

    private final ConfirmationTokenService confirmationTokenService;

    private final ConfirmationRequestService confirmationRequestService;

    private final EmailConfirmationTokenRepository tokenRepository;

    public EmailConfirmationServiceImpl(
            Clock clock,
            @Value("${app.confirmation.resend-cooldown}") Duration resendCooldown,
            UserRepository userRepository,
            ConfirmationTokenService confirmationTokenService,
            ConfirmationRequestService confirmationRequestService,
            EmailConfirmationTokenRepository tokenRepository
    ) {
        this.clock = clock;
        this.resendCooldown = resendCooldown;
        this.userRepository = userRepository;
        this.confirmationTokenService = confirmationTokenService;
        this.confirmationRequestService = confirmationRequestService;
        this.tokenRepository = tokenRepository;
    }

    @Override
    @Transactional
    public void confirm(String rawToken) {
        EmailConfirmationToken token = tokenRepository
                .findByTokenHash(confirmationTokenService.hash(rawToken))
                .orElseThrow(InvalidConfirmationTokenException::new);

        if (!token.getExpiresAt().isAfter(clock.instant())) {
            throw new InvalidConfirmationTokenException();
        }

        User user = token.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.delete(token);

        log.info("Confirmed user '{}'.", user.getId());
    }

    @Override
    @Transactional
    public void resend(String email) {
        userRepository
                .findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> !user.isEnabled())
                .ifPresent(this::resendUnlessThrottled);
    }

    /**
     * The row lock serialises concurrent resends, so the second one sees the first one's token and is
     * throttled.
     */
    private void resendUnlessThrottled(User user) {
        userRepository.lockById(user.getId());
        if (isInsideCooldown(user)) {
            return;
        }

        tokenRepository.deleteByUserId(user.getId());
        confirmationRequestService.requestConfirmation(user);
    }

    private boolean isInsideCooldown(User user) {
        Instant cooldownStart = clock.instant().minus(resendCooldown);

        return tokenRepository
                .findFirstByUserIdOrderByIssuedAtDesc(user.getId())
                .map(EmailConfirmationToken::getIssuedAt)
                .filter(issuedAt -> issuedAt.isAfter(cooldownStart))
                .isPresent();
    }
}
