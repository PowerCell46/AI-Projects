package com.peter_gerdzhikov.url_shortener_backend.interceptors;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.peter_gerdzhikov.url_shortener_backend.exceptions.ShortUrlNotFoundException;
import com.peter_gerdzhikov.url_shortener_backend.utilities.ShortCodeFormat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards the root-level {@code GET /{code}} redirect route. Rejects any single path segment that
 * isn't a well-formed short code before it reaches {@code RedirectController}, so scanner probes
 * (e.g. {@code /.env}, {@code /wp-config.php}) never trigger a database lookup. Deliberately mirrors
 * the unknown-code 404 body so a probe response is indistinguishable from a genuine miss.
 */
@Component
public class ScannerProbeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String code = extractCode(request);
        if (!ShortCodeFormat.isValid(code)) {
            throw new ShortUrlNotFoundException();
        }

        return true;
    }

    private String extractCode(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
