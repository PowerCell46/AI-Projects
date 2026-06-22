package com.wealthbuilder.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Per-IP rate limiter for the login endpoint. Tracks attempt counts in a fixed sliding window;
 * once the limit is exceeded the request is rejected with 429 before it reaches the controller.
 *
 * <p>The in-memory map grows at most to the number of distinct IPs that have attempted a login
 * within the current window — entries reset automatically on the next request after the window
 * expires. No cleanup thread is needed for a catalog-sized caller set.
 *
 * <p>Client IPs are read from {@code X-Forwarded-For} (set by the nginx edge proxy) with a
 * fallback to {@code getRemoteAddr()}. In deployments without a trusted proxy, the forwarded
 * header should be stripped or validated upstream to prevent spoofing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    // Allow 10 attempts per 15-minute window. Generous enough for legitimate users (forgotten
    // password, multiple devices) while still making online guessing infeasible.
    private static final int MAX_ATTEMPTS = 10;

    private static final long WINDOW_MS = 15L * 60 * 1_000;

    // Each entry is a two-element array: [windowStartMs, attemptCount].
    // ConcurrentHashMap.compute provides per-key atomicity without external locking.
    private final ConcurrentHashMap<String, long[]> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!LOGIN_PATH.equals(request.getRequestURI())
                || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        final String ip = resolveClientIp(request);

        if (isRateLimited(ip)) {
            log.warn("Login rate limit exceeded for IP '{}'.", ip);
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Atomically increments the attempt counter for the given IP within the current window,
     * resetting the window if it has expired. Returns true when the counter exceeds the limit.
     */
    private boolean isRateLimited(String ip) {
        final long now = System.currentTimeMillis();

        final long[] slot = attemptsByIp.compute(ip, (key, current) -> {
            if (current == null || now - current[0] >= WINDOW_MS) {
                return new long[]{now, 1};
            }

            return new long[]{current[0], current[1] + 1};
        });

        return slot[1] > MAX_ATTEMPTS;
    }

    private String resolveClientIp(HttpServletRequest request) {
        final String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter()
                .write("{\"status\":429,\"detail\":\"Too many login attempts. Please wait before trying again.\"}");
    }
}
