package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.MutableClock;

@ExtendWith(MockitoExtension.class)
class UnconfirmedUserCleanupServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-10T04:00:00Z");

    private static final Duration RETENTION = Duration.ofDays(7);

    @Mock
    private UserRepository userRepository;

    private UnconfirmedUserCleanupServiceImpl cleanupService;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock();
        clock.setInstant(NOW);
        cleanupService = new UnconfirmedUserCleanupServiceImpl(clock, RETENTION, userRepository);
    }

    @Test
    void should_delete_users_created_before_now_minus_the_retention_and_return_the_count() {
        when(userRepository.deleteUnconfirmedCreatedBefore(NOW.minus(RETENTION))).thenReturn(3);

        int deleted = cleanupService.deleteExpiredUnconfirmedUsers();

        assertThat(deleted).isEqualTo(3);
        verify(userRepository).deleteUnconfirmedCreatedBefore(NOW.minus(RETENTION));
    }
}
