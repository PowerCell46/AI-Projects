package com.wealthbuilder.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
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
}
