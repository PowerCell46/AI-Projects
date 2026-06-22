package com.wealthbuilder.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


/**
 * Allows the Vite dev SPA (a different origin in dev) to call the API. Credentials are enabled so
 * the browser sends the httpOnly auth cookie; that requires reflecting a specific origin rather
 * than {@code *}, which {@code setAllowedOriginPatterns} does.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final AppProperties appProperties;

    private final Environment environment;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        ensureNoWildcardInProd();

        final CorsConfiguration configuration = new CorsConfiguration();
        // Patterns (not exact origins) so localhost / 127.0.0.1 match on any dev port and
        // the matched origin is reflected back. Configurable via app.frontend-base-uris.
        configuration.setAllowedOriginPatterns(appProperties.getFrontendBaseUris());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for the browser to send and accept the httpOnly auth cookie cross-origin.
        configuration.setAllowCredentials(true);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    /**
     * Fails fast at startup if any CORS origin pattern contains the port-wildcard {@code [*]}
     * while the {@code prod} profile is active. A wildcard origin reflects every localhost port
     * back as a trusted origin; that is acceptable for local development but must never reach
     * production. Set {@code FRONTEND_BASE_URIS} to the exact deployed origin(s) for prod.
     */
    private void ensureNoWildcardInProd() {
        if (!environment.matchesProfiles("prod")) {
            return;
        }

        final boolean hasWildcard = appProperties.getFrontendBaseUris()
                .stream()
                .anyMatch(uri -> uri.contains("[*]"));

        if (hasWildcard) {
            throw new IllegalStateException(
                    "CORS is configured with a port-wildcard pattern ([*]) while the 'prod' profile is active. "
                    + "Set FRONTEND_BASE_URIS to the exact production origin(s) only "
                    + "(e.g. https://app.example.com).");
        }
    }
}
