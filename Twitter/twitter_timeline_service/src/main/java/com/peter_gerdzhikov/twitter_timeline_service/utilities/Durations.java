package com.peter_gerdzhikov.twitter_timeline_service.utilities;

import java.time.Duration;

public class Durations {

    private Durations() {
    }

    public static Duration requirePositive(Duration duration, String propertyName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + " must be positive, but was " + duration + ".");
        }

        return duration;
    }
}
