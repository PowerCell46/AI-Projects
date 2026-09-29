package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidConfirmationTokenException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationRequestService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationTokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.MutableClock;

@ExtendWith(MockitoExtension.class)
class EmailConfirmationServiceImplTest {

    private static final String RAW_TOKEN = "raw-token";

    private static final String TOKEN_HASH = "token-hash";

    private static final String EMAIL = "peterg@example.test";

    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final UUID userId = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConfirmationTokenService confirmationTokenService;

    @Mock
    private ConfirmationRequestService confirmationRequestService;

    @Mock
    private EmailConfirmationTokenRepository tokenRepository;

    private MutableClock clock;

    private EmailConfirmationServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        clock.setInstant(NOW);
        service = new EmailConfirmationServiceImpl(
                clock,
                COOLDOWN,
                userRepository,
                confirmationTokenService,
                confirmationRequestService,
                tokenRepository
        );
    }

    @Nested
    class Confirm {

        @Test
        void should_enable_the_user_and_delete_the_token_when_the_token_is_valid() {
            User user = pendingUser();
            EmailConfirmationToken token = tokenExpiringAt(user, NOW.plusSeconds(1));
            when(confirmationTokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

            service.confirm(RAW_TOKEN);

            assertThat(user.isEnabled()).isTrue();
            InOrder order = inOrder(userRepository, tokenRepository);
            order.verify(userRepository).save(user);
            order.verify(tokenRepository).delete(token);
        }

        @Test
        void should_reject_an_unknown_token() {
            when(confirmationTokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(RAW_TOKEN))
                    .isInstanceOf(InvalidConfirmationTokenException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void should_reject_a_token_that_expires_exactly_now() {
            User user = pendingUser();
            when(confirmationTokenService.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
            when(tokenRepository.findByTokenHash(TOKEN_HASH))
                    .thenReturn(Optional.of(tokenExpiringAt(user, NOW)));

            assertThatThrownBy(() -> service.confirm(RAW_TOKEN))
                    .isInstanceOf(InvalidConfirmationTokenException.class);

            assertThat(user.isEnabled()).isFalse();
            verify(tokenRepository, never()).delete(any());
        }
    }

    @Nested
    class Resend {

        @Test
        void should_do_nothing_when_the_email_is_unknown() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            service.resend(EMAIL);

            verify(confirmationRequestService, never()).requestConfirmation(any());
        }

        @Test
        void should_do_nothing_when_the_user_is_already_confirmed() {
            User user = pendingUser();
            user.setEnabled(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            service.resend(EMAIL);

            verify(confirmationRequestService, never()).requestConfirmation(any());
        }

        @Test
        void should_look_the_email_up_lowercased() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            service.resend("PeterG@Example.TEST");

            verify(userRepository).findByEmail(EMAIL);
        }

        @Test
        void should_replace_the_tokens_when_the_newest_one_is_older_than_the_cooldown() {
            User user = pendingUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(userId))
                    .thenReturn(Optional.of(tokenIssuedAt(NOW.minus(COOLDOWN).minusSeconds(1))));

            service.resend(EMAIL);

            InOrder order = inOrder(userRepository, tokenRepository, confirmationRequestService);
            order.verify(userRepository).lockById(userId);
            order.verify(tokenRepository).deleteByUserId(userId);
            order.verify(confirmationRequestService).requestConfirmation(user);
        }

        @Test
        void should_request_a_confirmation_when_the_user_has_no_token() {
            User user = pendingUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(userId)).thenReturn(Optional.empty());

            service.resend(EMAIL);

            verify(confirmationRequestService).requestConfirmation(user);
        }

        @Test
        void should_do_nothing_when_the_newest_token_is_inside_the_cooldown() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pendingUser()));
            when(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(userId))
                    .thenReturn(Optional.of(tokenIssuedAt(NOW.minus(COOLDOWN).plusSeconds(1))));

            service.resend(EMAIL);

            verify(tokenRepository, never()).deleteByUserId(any());
            verify(confirmationRequestService, never()).requestConfirmation(any());
        }

        @Test
        void should_do_nothing_when_the_newest_token_was_issued_this_instant() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pendingUser()));
            when(tokenRepository.findFirstByUserIdOrderByIssuedAtDesc(userId))
                    .thenReturn(Optional.of(tokenIssuedAt(NOW)));

            service.resend(EMAIL);

            verify(confirmationRequestService, never()).requestConfirmation(any());
        }
    }

    private User pendingUser() {
        User user = User.builder()
                .email(EMAIL)
                .username("PeterG")
                .password("hashed-password")
                .build();
        user.setId(userId);
        return user;
    }

    private EmailConfirmationToken tokenExpiringAt(User user, Instant expiresAt) {
        return EmailConfirmationToken.builder()
                .user(user)
                .tokenHash(TOKEN_HASH)
                .issuedAt(NOW.minusSeconds(10))
                .expiresAt(expiresAt)
                .build();
    }

    private EmailConfirmationToken tokenIssuedAt(Instant issuedAt) {
        return EmailConfirmationToken.builder()
                .tokenHash(TOKEN_HASH)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofHours(24)))
                .build();
    }
}
