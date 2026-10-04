package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.AuthService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.UnconfirmedUserCleanupService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUser;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;

@SpringBootTest
@ActiveProfiles("test")
class UnconfirmedUserCleanupServiceIntegrationTest extends AbstractMinioIntegrationTest {

    @Value("${app.users.unconfirmed-cleanup.retention}")
    private Duration retention;

    @Autowired
    private Clock clock;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Autowired
    private UnconfirmedUserCleanupService cleanupService;

    @Test
    void should_delete_an_unconfirmed_user_past_the_retention_and_cascade_its_tokens() {
        User stale = registeredUserCreatedAt(false, staleInstant());

        cleanupService.deleteExpiredUnconfirmedUsers();

        assertThat(userRepository.findById(stale.getId())).isEmpty();
        assertThat(tokenCountOf(stale.getId())).isZero();
    }

    @Test
    void should_keep_an_unconfirmed_user_inside_the_retention_and_its_tokens() {
        User fresh = registeredUserCreatedAt(false, clock.instant().minus(retention).plusSeconds(60));

        cleanupService.deleteExpiredUnconfirmedUsers();

        assertThat(userRepository.findById(fresh.getId())).isPresent();
        assertThat(tokenCountOf(fresh.getId())).isEqualTo(1);
    }

    @Test
    void should_keep_a_confirmed_user_past_the_retention() {
        User confirmed = registeredUserCreatedAt(true, staleInstant());

        cleanupService.deleteExpiredUnconfirmedUsers();

        assertThat(userRepository.findById(confirmed.getId())).isPresent();
    }

    @Test
    void should_leave_the_outbox_rows_of_a_deleted_user_untouched() {
        User stale = registeredUserCreatedAt(false, staleInstant());

        cleanupService.deleteExpiredUnconfirmedUsers();

        assertThat(outboxRepository.findAll())
                .filteredOn(row -> row.getMessageKey().equals(stale.getId().toString()))
                .hasSize(1);
    }

    @Test
    void should_return_the_number_of_deleted_users() {
        registeredUserCreatedAt(false, staleInstant());
        registeredUserCreatedAt(false, staleInstant());

        assertThat(cleanupService.deleteExpiredUnconfirmedUsers()).isGreaterThanOrEqualTo(2);
    }

    private Instant staleInstant() {
        return clock.instant().minus(retention).minusSeconds(60);
    }

    private long tokenCountOf(UUID userId) {
        return tokenRepository
                .findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .count();
    }

    private User registeredUserCreatedAt(boolean enabled, Instant createdAt) {
        TestUser credentials = TestUsers.unique();
        User user = authService.register(RegisterRequestDTO.builder()
                .email(credentials.getEmail())
                .username(credentials.getUsername())
                .password(credentials.getPassword())
                .build());
        jdbcTemplate.update(
                "UPDATE users SET created_at = ?, enabled = ? WHERE id = ?",
                Timestamp.from(createdAt),
                enabled,
                user.getId()
        );

        return user;
    }
}
