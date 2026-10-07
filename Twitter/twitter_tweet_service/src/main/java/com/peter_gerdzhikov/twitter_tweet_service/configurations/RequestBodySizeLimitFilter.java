package com.peter_gerdzhikov.twitter_tweet_service.configurations;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.BodySizeLimitingRequestWrapper;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered first so an oversized body is refused before any JSON or
 * multipart parsing work happens.
 *
 * <p>The tweet-create route gets the larger cap for its images; every other route keeps the small one. Only
 * that route takes multipart: the container parses it without going through the counting wrapper, so on any
 * other route it is refused up front.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final String CREATE_TWEET_PATH = "/api/v1/tweets";

    private static final String UNSUPPORTED_BODY_MESSAGE = "This route does not accept multipart bodies.";

    private final long maxBodyBytes;

    private final long maxTweetBodyBytes;

    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            @Value("${app.request.max-tweet-body-bytes}") long maxTweetBodyBytes,
            ObjectMapper objectMapper
    ) {
        this.maxBodyBytes = maxBodyBytes;
        this.maxTweetBodyBytes = maxTweetBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isMultipart(request) && !isCreateTweet(request)) {
            log.warn("Rejected a multipart request to '{}'.", request.getRequestURI());
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_BODY_MESSAGE);
            return;
        }

        long maxBytes = isCreateTweet(request) ? maxTweetBodyBytes : maxBodyBytes;

        if (request.getContentLengthLong() > maxBytes) {
            log.warn("Rejected a request to '{}' declaring an oversized body.", request.getRequestURI());
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.CONTENT_TOO_LARGE, RequestBodyTooLargeException.MESSAGE);
            return;
        }

        // A chunked request declares no length, so the wrapper counts what is actually read instead;
        // GlobalExceptionHandler turns the resulting exception into the same 413.
        filterChain.doFilter(new BodySizeLimitingRequestWrapper(request, maxBytes), response);
    }

    private boolean isCreateTweet(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && CREATE_TWEET_PATH.equals(request.getRequestURI());
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();

        return contentType != null && contentType
                .toLowerCase()
                .startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }
}
