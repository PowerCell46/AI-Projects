package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code maxRequests} per key in each window, counted in memory, so it holds per instance only.
 * Expired windows are swept once the map outgrows {@link #SWEEP_THRESHOLD}, which keeps it bounded by the
 * number of distinct keys seen within one window.
 */
public class FixedWindowRateLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxRequests;

    private final long windowMillis;

    private final Clock clock;

    private final Map<String, Window> windowsByKey = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (windowsByKey.size() > SWEEP_THRESHOLD) {
            windowsByKey
                    .values()
                    .removeIf(window -> window.isExpiredAt(now, windowMillis));
        }

        Window window = windowsByKey.compute(key, (ignored, current) -> next(current, now));

        return window.requestCount <= maxRequests;
    }

    private Window next(Window current, long now) {
        if (current == null || current.isExpiredAt(now, windowMillis)) {
            return new Window(now, 1);
        }

        return new Window(current.startedAtMillis, current.requestCount + 1);
    }

    private static final class Window {

        private final long startedAtMillis;

        private final int requestCount;

        private Window(long startedAtMillis, int requestCount) {
            this.startedAtMillis = startedAtMillis;
            this.requestCount = requestCount;
        }

        private boolean isExpiredAt(long now, long windowMillis) {
            return now - startedAtMillis >= windowMillis;
        }
    }
}
