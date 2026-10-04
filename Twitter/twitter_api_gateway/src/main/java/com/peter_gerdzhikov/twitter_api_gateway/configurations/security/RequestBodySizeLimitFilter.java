package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.BodySizeLimitingRequestWrapper;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered ahead of the security chain so an oversized anonymous body
 * (register, login) is refused before any authentication or JSON parsing work happens.
 *
 * <p>The two picture-upload routes get the upload cap and tweet creation gets the tweet cap; every other
 * route keeps the small one.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final Set<String> UPLOAD_PATHS = Set.of(
            "/api/v1/users/me/profile-picture",
            "/api/v1/users/me/cover-picture"
    );

    private static final String TWEET_CREATE_PATH = "/api/v1/tweets";

    private final long maxBodyBytes;

    private final long maxTweetBodyBytes;

    private final long maxUploadBodyBytes;

    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            @Value("${app.request.max-upload-body-bytes}") long maxUploadBodyBytes,
            @Value("${app.request.max-tweet-body-bytes}") long maxTweetBodyBytes,
            ObjectMapper objectMapper
    ) {
        this.maxBodyBytes = maxBodyBytes;
        this.maxUploadBodyBytes = maxUploadBodyBytes;
        this.maxTweetBodyBytes = maxTweetBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
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

    private long maxBytesFor(HttpServletRequest request) {
        if (isPictureUpload(request)) {
            return maxUploadBodyBytes;
        }

        return isTweetCreation(request) ? maxTweetBodyBytes : maxBodyBytes;
    }

    private boolean isPictureUpload(HttpServletRequest request) {
        return HttpMethod.PUT.matches(request.getMethod())
                && UPLOAD_PATHS.contains(request.getRequestURI());
    }

    private boolean isTweetCreation(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && TWEET_CREATE_PATH.equals(request.getRequestURI());
    }
}
