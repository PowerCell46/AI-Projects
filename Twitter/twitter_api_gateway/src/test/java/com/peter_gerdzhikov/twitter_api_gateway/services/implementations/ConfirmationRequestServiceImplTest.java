package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationTokenService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.OutboxService;

@ExtendWith(MockitoExtension.class)
class ConfirmationRequestServiceImplTest {

    private static final String TOPIC = "user.confirmation-requested";

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private static final String LINK_BASE_URL = "http://localhost/confirm";

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private User user;

    @Mock
    private OutboxService outboxService;

    private ConfirmationRequestServiceImpl service;

    @Mock
    private EmailConfirmationTokenRepository tokenRepository;

    @Mock
    private ConfirmationTokenService confirmationTokenService;

    @BeforeEach
    void setUp() {
        service = new ConfirmationRequestServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC),
                TOPIC,
                TOKEN_TTL,
                LINK_BASE_URL,
                outboxService,
                confirmationTokenService,
                tokenRepository
        );
        user = User.builder()
                .username("PeterG")
                .email("peterg@example.test")
                .password("hashed-password")
                .build();
        user.setId(UUID.randomUUID());
        when(confirmationTokenService.generateRawToken()).thenReturn("raw-token");
        when(confirmationTokenService.hash("raw-token")).thenReturn("hashed-token");
    }

    @Test
    void should_store_the_hash_not_the_raw_token_for_the_user() {
        service.requestConfirmation(user);

        EmailConfirmationToken saved = savedToken();

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).isEqualTo("hashed-token");
    }

    @Test
    void should_issue_the_token_from_the_clock_and_expire_it_after_the_ttl() {
        service.requestConfirmation(user);

        EmailConfirmationToken saved = savedToken();

        assertThat(saved.getIssuedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(TOKEN_TTL));
    }

    @Test
    void should_enqueue_one_event_on_the_topic_keyed_by_the_user_id() {
        service.requestConfirmation(user);

        assertThat(enqueuedEvents(1)).hasSize(1);
    }

    @Test
    void should_carry_the_user_and_the_raw_token_link_in_the_event() {
        service.requestConfirmation(user);

        UserConfirmationRequestedEventDTO event = enqueuedEvents(1).getFirst();

        assertThat(event.getUserId()).isEqualTo(user.getId());
        assertThat(event.getEmail()).isEqualTo(user.getEmail());
        assertThat(event.getUsername()).isEqualTo(user.getUsername());
        assertThat(event.getConfirmationUrl()).isEqualTo("http://localhost/confirm?token=raw-token");
        assertThat(event.getExpiresAt()).isEqualTo(NOW.plus(TOKEN_TTL));
    }

    @Test
    void should_give_every_event_a_fresh_event_id() {
        service.requestConfirmation(user);
        service.requestConfirmation(user);

        List<UserConfirmationRequestedEventDTO> events = enqueuedEvents(2);

        assertThat(events.get(0).getEventId()).isNotEqualTo(events.get(1).getEventId());
    }

    private EmailConfirmationToken savedToken() {
        ArgumentCaptor<EmailConfirmationToken> captor = ArgumentCaptor.forClass(EmailConfirmationToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private List<UserConfirmationRequestedEventDTO> enqueuedEvents(int expectedCalls) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService, times(expectedCalls)).enqueue(eq(TOPIC), eq(user.getId().toString()), captor.capture());

        return captor
                .getAllValues()
                .stream()
                .map(UserConfirmationRequestedEventDTO.class::cast)
                .toList();
    }
}
