package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import static com.peter_gerdzhikov.signal_flow_api_gateway.configurations.InterestTopicRoutesConfiguration.CATEGORIES_PATH;
import static com.peter_gerdzhikov.signal_flow_api_gateway.configurations.InterestTopicRoutesConfiguration.INTEREST_TOPICS_PATH;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.BodySizeLimitingRequestWrapper;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered ahead of the security chain so an oversized anonymous body
 * (register, login) is refused before any authentication or JSON parsing work happens. The topic routes
 * get their own, larger cap, because the topic service's limits are counted in characters, not bytes.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher TOPIC_ROUTES = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(CATEGORIES_PATH),
            PathPatternRequestMatcher.withDefaults().matcher(INTEREST_TOPICS_PATH)
    );

    private final long maxBodyBytes;

    private final long maxTopicBodyBytes;

    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            @Value("${app.request.max-topic-body-bytes}") long maxTopicBodyBytes,
            ObjectMapper objectMapper
    ) {
        this.maxBodyBytes = maxBodyBytes;
        this.maxTopicBodyBytes = maxTopicBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long limit = limitFor(request);
        if (request.getContentLengthLong() > limit) {
            log.warn("Rejected a request to '{}' declaring an oversized body.", request.getRequestURI());
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.CONTENT_TOO_LARGE, RequestBodyTooLargeException.MESSAGE);
            return;
        }

        // A chunked request declares no length, so the wrapper counts what is actually read instead;
        // GlobalExceptionHandler turns the resulting exception into the same 413.
        filterChain.doFilter(new BodySizeLimitingRequestWrapper(request, limit), response);
    }

    private long limitFor(HttpServletRequest request) {
        return TOPIC_ROUTES.matches(request) ? maxTopicBodyBytes : maxBodyBytes;
    }
}
