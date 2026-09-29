package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationRequestService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationTokenService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.OutboxService;

@Service
public class ConfirmationRequestServiceImpl implements ConfirmationRequestService {

    private final Clock clock;

    private final String topic;

    private final Duration tokenTtl;

    private final String linkBaseUrl;

    private final OutboxService outboxService;

    private final ConfirmationTokenService confirmationTokenService;

    private final EmailConfirmationTokenRepository tokenRepository;

    public ConfirmationRequestServiceImpl(
            Clock clock,
            @Value("${app.kafka.user-confirmation-requested.name}") String topic,
            @Value("${app.confirmation.ttl}") Duration tokenTtl,
            @Value("${app.confirmation.link-base-url}") String linkBaseUrl,
            OutboxService outboxService,
            ConfirmationTokenService confirmationTokenService,
            EmailConfirmationTokenRepository tokenRepository
    ) {
        this.clock = clock;
        this.topic = topic;
        this.tokenTtl = tokenTtl;
        this.linkBaseUrl = linkBaseUrl;
        this.outboxService = outboxService;
        this.confirmationTokenService = confirmationTokenService;
        this.tokenRepository = tokenRepository;
    }

    @Override
    public void requestConfirmation(User user) {
        String rawToken = confirmationTokenService.generateRawToken();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);

        tokenRepository.save(newToken(user, rawToken, issuedAt, expiresAt));
        outboxService.enqueue(topic, user.getId().toString(), newEvent(user, rawToken, expiresAt));
    }

    private EmailConfirmationToken newToken(User user, String rawToken, Instant issuedAt, Instant expiresAt) {
        return EmailConfirmationToken.builder()
                .user(user)
                .tokenHash(confirmationTokenService.hash(rawToken))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private UserConfirmationRequestedEventDTO newEvent(User user, String rawToken, Instant expiresAt) {
        return UserConfirmationRequestedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .confirmationUrl(linkBaseUrl + "?token=" + rawToken)
                .expiresAt(expiresAt)
                .build();
    }
}
