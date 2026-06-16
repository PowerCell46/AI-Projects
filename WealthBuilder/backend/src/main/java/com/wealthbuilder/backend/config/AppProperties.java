package com.wealthbuilder.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


/**
 * Application-level settings bound from the {@code app.*} configuration tree.
 *
 * @param frontendBaseUri browser-facing SPA origin, allowed by CORS
 * @param jwt             JWT signing material and lifetime
 * @param moderator       credentials for the moderator seeded at startup
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        String frontendBaseUri,

        Jwt jwt,

        Moderator moderator
) {

    /**
     * @param secret HMAC signing secret (>= 256 bits for HS256)
     * @param ttl    how long an issued access token stays valid
     */
    public record Jwt(

            String secret,

            Duration ttl
    ) {
    }

    /**
     * @param username seeded moderator username
     * @param password seeded moderator raw password; blank disables seeding
     */
    public record Moderator(

            String username,

            String password
    ) {
    }
}
