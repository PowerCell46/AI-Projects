package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private static final int MAX_REQUESTS = 3;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private Clock clock;

    private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        rateLimiter = new FixedWindowRateLimiter(MAX_REQUESTS, WINDOW, clock);
    }

    @Test
    void should_allow_requests_up_to_the_maximum_within_a_window() {
        for (int request = 0; request < MAX_REQUESTS; request++) {
            assertThat(rateLimiter.tryAcquire("1.2.3.4")).isTrue();
        }
    }

    @Test
    void should_refuse_the_request_after_the_maximum_within_a_window() {
        acquireMaximum("1.2.3.4");

        assertThat(rateLimiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(rateLimiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void should_count_each_key_separately() {
        acquireMaximum("1.2.3.4");

        assertThat(rateLimiter.tryAcquire("5.6.7.8")).isTrue();
    }

    @Test
    void should_allow_requests_again_once_the_window_has_passed() {
        acquireMaximum("1.2.3.4");
        when(clock.millis()).thenReturn(WINDOW.toMillis());

        assertThat(rateLimiter.tryAcquire("1.2.3.4")).isTrue();
    }

    @Test
    void should_keep_refusing_until_the_window_has_passed() {
        acquireMaximum("1.2.3.4");
        when(clock.millis()).thenReturn(WINDOW.toMillis() - 1);

        assertThat(rateLimiter.tryAcquire("1.2.3.4")).isFalse();
    }

    private void acquireMaximum(String key) {
        for (int request = 0; request < MAX_REQUESTS; request++) {
            rateLimiter.tryAcquire(key);
        }
    }
}
