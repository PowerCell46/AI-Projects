package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * The only guard on {@code /internal/v1/**}, which the security chain lets through and which sits on the same
 * public port as everything else. A missing or wrong secret answers the same 404 an unknown path gets, so the
 * routes can't be told apart from absent ones. Ordered ahead of the security chain, like the body-size filter.
 *
 * <p>The path is read the way Spring MVC resolves it (decoded, {@code //} collapsed, path parameters dropped),
 * so a raw variant of the URL can't slip past the prefix check and still reach a controller.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalApiSecretFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Internal-Secret";

    private static final int MIN_SECRET_BYTES = 32;

    private static final String INTERNAL_PATH = "/internal/v1";

    private static final String UNKNOWN_PATH_MESSAGE = "No resource found for this path.";

    private final byte[] secret;

    private final ObjectMapper objectMapper;

    public InternalApiSecretFilter(@Value("${app.internal-api.secret}") String secret, ObjectMapper objectMapper) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.internal-api.secret must be at least 32 bytes.");
        }

        this.secret = secretBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isInternalPath(request) && !hasValidSecret(request)) {
            log.warn("Rejected a request to the internal API without a valid secret.");
            ErrorResponseWriter.write(response, objectMapper, HttpStatus.NOT_FOUND, UNKNOWN_PATH_MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);

        return path.equals(INTERNAL_PATH) || path.startsWith(INTERNAL_PATH + "/");
    }

    private boolean hasValidSecret(HttpServletRequest request) {
        String presented = request.getHeader(SECRET_HEADER);

        return presented != null
                && MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8));
    }
}
