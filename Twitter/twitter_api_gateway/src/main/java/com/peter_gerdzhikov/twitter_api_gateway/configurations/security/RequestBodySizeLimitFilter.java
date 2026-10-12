package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_shared.web.RequestBodySizeLimitFilterSupport;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caps the raw request body, which the field-level {@code @Size} constraints cannot do - they only run
 * once the body is already parsed. Ordered ahead of the security chain so an oversized anonymous body
 * (register, login) is refused before any authentication or JSON parsing work happens.
 *
 * <p>The two picture-upload routes get the upload cap and tweet creation gets the tweet cap; every other
 * route keeps the small one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends RequestBodySizeLimitFilterSupport {

    private static final Set<String> UPLOAD_PATHS = Set.of(
            "/api/v1/users/me/profile-picture",
            "/api/v1/users/me/cover-picture"
    );

    private static final String TWEET_CREATE_PATH = "/api/v1/tweets";

    private final long maxBodyBytes;

    private final long maxTweetBodyBytes;

    private final long maxUploadBodyBytes;

    public RequestBodySizeLimitFilter(
            @Value("${app.request.max-body-bytes}") long maxBodyBytes,
            @Value("${app.request.max-upload-body-bytes}") long maxUploadBodyBytes,
            @Value("${app.request.max-tweet-body-bytes}") long maxTweetBodyBytes,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.maxBodyBytes = maxBodyBytes;
        this.maxUploadBodyBytes = maxUploadBodyBytes;
        this.maxTweetBodyBytes = maxTweetBodyBytes;
    }

    @Override
    protected long maxBytesFor(HttpServletRequest request) {
        if (isPictureUpload(request)) {
            return maxUploadBodyBytes;
        }

        return isTweetCreation(request) ? maxTweetBodyBytes : maxBodyBytes;
    }

    private boolean isPictureUpload(HttpServletRequest request) {
        return HttpMethod.PUT.matches(request.getMethod()) && UPLOAD_PATHS.contains(request.getRequestURI());
    }

    private boolean isTweetCreation(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && TWEET_CREATE_PATH.equals(request.getRequestURI());
    }
}
