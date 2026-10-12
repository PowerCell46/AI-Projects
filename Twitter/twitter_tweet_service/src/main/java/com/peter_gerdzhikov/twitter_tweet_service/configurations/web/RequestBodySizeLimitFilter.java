package com.peter_gerdzhikov.twitter_tweet_service.configurations.web;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_shared.web.ErrorResponseWriter;
import com.peter_gerdzhikov.twitter_shared.web.RequestBodySizeLimitFilterSupport;

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
public class RequestBodySizeLimitFilter extends RequestBodySizeLimitFilterSupport {

    private static final String CREATE_TWEET_PATH = "/api/v1/tweets";

    private static final String UNSUPPORTED_BODY_MESSAGE = "This route does not accept multipart bodies.";

    private final long maxBodyBytes;

    private final long maxTweetBodyBytes;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            @Value("${app.request.max-tweet-body-bytes}") long maxTweetBodyBytes,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.maxBodyBytes = maxBodyBytes;
        this.maxTweetBodyBytes = maxTweetBodyBytes;
    }

    @Override
    protected boolean rejectEarly(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isMultipart(request) || isCreateTweet(request)) {
            return false;
        }

        log.warn("Rejected a multipart request to '{}'.", request.getRequestURI());
        ErrorResponseWriter.write(response, objectMapper(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_BODY_MESSAGE);

        return true;
    }

    @Override
    protected long maxBytesFor(HttpServletRequest request) {
        return isCreateTweet(request) ? maxTweetBodyBytes : maxBodyBytes;
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
