package com.peter_gerdzhikov.twitter_shared.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Each service caps its own routes through {@link #maxBytesFor} and
 * may refuse some requests up front through {@link #rejectEarly}.
 */
@Slf4j
public abstract class RequestBodySizeLimitFilterSupport extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    protected RequestBodySizeLimitFilterSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (rejectEarly(request, response)) {
            return;
        }

        long maxBytes = maxBytesFor(request);

        if (request.getContentLengthLong() > maxBytes) {
            log.warn("Rejected a request to '{}' declaring an oversized body.", request.getRequestURI());
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.CONTENT_TOO_LARGE, RequestBodyTooLargeException.MESSAGE);
            return;
        }

        // A chunked request declares no length, so the wrapper counts what is actually read instead;
        // GlobalExceptionHandler turns the resulting exception into the same 413.
        filterChain.doFilter(new BodySizeLimitingRequestWrapper(request, maxBytes), response);
    }

    /**
     * The mapper a {@link #rejectEarly} override answers with.
     */
    protected ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Refuses a request before the size check, answering it straight away. The default accepts everything.
     *
     * @return whether the request was answered and must not continue down the chain
     */
    protected boolean rejectEarly(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return false;
    }

    /**
     * The cap for this request, after {@link #rejectEarly} accepted it.
     */
    protected abstract long maxBytesFor(HttpServletRequest request);
}
