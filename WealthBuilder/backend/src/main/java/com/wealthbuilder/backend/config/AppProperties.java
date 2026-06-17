package com.wealthbuilder.backend.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


/**
 * Application-level settings bound from the {@code app.*} configuration tree. Bound via
 * constructor binding — the single generated constructor makes each value final, so the
 * configuration is immutable once the context starts.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Browser-facing SPA origin, allowed by CORS. */
    private final String frontendBaseUri;

    /** JWT signing material and lifetime. */
    private final Jwt jwt;

    /** Credentials for the moderator seeded at startup. */
    private final Moderator moderator;

    @Getter
    @RequiredArgsConstructor
    public static class Jwt {

        /** HMAC signing secret (>= 256 bits for HS256). */
        private final String secret;

        /** How long an issued access token stays valid. */
        private final Duration ttl;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Moderator {

        /** Seeded moderator username. */
        private final String username;

        /** Seeded moderator raw password; blank disables seeding. */
        private final String password;
    }
}
