package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security.ratelimit;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.ClientAddressResolver;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Limits login and register per client address. Both are unauthenticated and each costs a bcrypt hash, so
 * without a limit parallel requests exhaust the CPU and credential stuffing is unbounded. Ordered right
 * after the body-size filter and ahead of the security chain, so a refused request costs no hashing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many requests. Try again later.";

    private static final RequestMatcher LIMITED_ROUTES = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/register")
    );

    private final int forwardedForIndexFromRight;

    private final long retryAfterSeconds;

    private final ObjectMapper objectMapper;

    private final FixedWindowRateLimiter rateLimiter;

    public AuthRateLimitFilter(
            @Value("${app.rate-limit.auth.max-requests}") int maxRequests,
            @Value("${app.rate-limit.auth.window}") Duration window,
            @Value("${app.rate-limit.forwarded-for-index-from-right}") int forwardedForIndexFromRight,
            ObjectMapper objectMapper
    ) {
        this.forwardedForIndexFromRight = forwardedForIndexFromRight;
        this.retryAfterSeconds = window.toSeconds();
        this.objectMapper = objectMapper;
        this.rateLimiter = new FixedWindowRateLimiter(maxRequests, window, Clock.systemUTC());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!LIMITED_ROUTES.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientAddress = ClientAddressResolver.resolve(request, forwardedForIndexFromRight);
        if (rateLimiter.tryAcquire(clientAddress)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limited a request to '{}' from '{}'.", request.getRequestURI(), clientAddress);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_MESSAGE);
    }
}
