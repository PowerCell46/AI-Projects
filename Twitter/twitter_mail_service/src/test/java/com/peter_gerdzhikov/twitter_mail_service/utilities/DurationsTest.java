package com.peter_gerdzhikov.twitter_mail_service.utilities;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    @Test
    void should_return_the_same_duration_when_it_is_positive() {
        Duration duration = Duration.ofMinutes(5);

        assertEquals(duration, Durations.requirePositive(duration, "app.some-ttl"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -86_400})
    void should_throw_naming_the_property_when_the_duration_is_zero_or_negative(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> Durations.requirePositive(duration, "app.some-ttl"));

        assertTrue(thrown.getMessage().contains("app.some-ttl"));
    }
}
