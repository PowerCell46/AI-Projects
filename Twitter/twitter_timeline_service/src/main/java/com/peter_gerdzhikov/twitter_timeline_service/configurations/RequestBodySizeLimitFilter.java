package com.peter_gerdzhikov.twitter_timeline_service.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_shared.web.RequestBodySizeLimitFilterSupport;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered first so an oversized body is refused before any JSON parsing
 * work happens. Every route keeps the one small cap: nothing here takes a large body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends RequestBodySizeLimitFilterSupport {

    private final long maxBodyBytes;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected long maxBytesFor(HttpServletRequest request) {
        return maxBodyBytes;
    }
}
