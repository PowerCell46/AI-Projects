package com.wealthbuilder.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


/**
 * Rejects any request whose declared body size exceeds the global cap before the body is read,
 * so an oversized payload (e.g. a huge base64 image) can't be buffered into memory just to be
 * rejected later by bean validation. Runs first so the check precedes all other processing.
 *
 * <p>Requests without a {@code Content-Length} (chunked) pass through here; per-field
 * {@code @Size} validation remains the backstop for those.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    // 15 MB: comfortably above the largest legitimate payload — a ~14 MB base64-encoded 10 MB image
    // plus the surrounding JSON — while still blocking grossly oversized bodies.
    private static final long MAX_REQUEST_BYTES = 15L * 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request body too large.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
