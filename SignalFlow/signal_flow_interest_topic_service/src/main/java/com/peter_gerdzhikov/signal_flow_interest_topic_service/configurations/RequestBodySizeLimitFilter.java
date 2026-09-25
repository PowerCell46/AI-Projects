package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.BodySizeLimitingRequestWrapper;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered ahead of the rest of the filter chain so an oversized body
 * is refused before any JSON parsing work happens. The topic routes get their own, larger cap, matching
 * the gateway's, because their field limits are counted in characters, not bytes.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final List<PathPattern> TOPIC_ROUTES = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/categories/**"),
            PathPatternParser.defaultInstance.parse("/api/v1/interest-topics/**")
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
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        boolean isTopicRoute = TOPIC_ROUTES
                .stream()
                .anyMatch(pattern -> pattern.matches(path));

        return isTopicRoute ? maxTopicBodyBytes : maxBodyBytes;
    }
}
