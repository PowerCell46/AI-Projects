package com.wealthbuilder.backend.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;


/**
 * Application-level settings bound from the {@code app.*} configuration tree. Bound via
 * constructor binding — the single generated constructor makes each value final, so the
 * configuration is immutable once the context starts.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Browser-facing SPA origins allowed by CORS (e.g. the localhost and 127.0.0.1 dev URLs). */
    private final List<String> frontendBaseUris;

    /** JWT signing material and lifetime. */
    private final Jwt jwt;

    /** Auth-cookie settings (the JWT rides in an httpOnly cookie, not the response body). */
    private final Auth auth;

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
    public static class Auth {

        /** Name of the httpOnly cookie carrying the JWT. */
        private final String cookieName;

        /** Whether the cookie is flagged {@code Secure} (true on https; false for http dev). */
        private final boolean cookieSecure;

        /** SameSite policy — {@code Lax} is the CSRF defense for the same-site SPA. */
        private final String cookieSameSite;
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
