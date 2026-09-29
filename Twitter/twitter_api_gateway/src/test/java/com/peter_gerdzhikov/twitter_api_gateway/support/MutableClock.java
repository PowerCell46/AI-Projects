package com.peter_gerdzhikov.twitter_api_gateway.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that stands still until a test moves it. Truncated to microseconds so an instant it hands out
 * round-trips through Postgres unchanged.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now = new AtomicReference<>(currentInstant());

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    public void setInstant(Instant instant) {
        now.set(instant.truncatedTo(ChronoUnit.MICROS));
    }

    public void reset() {
        now.set(currentInstant());
    }

    private static Instant currentInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
