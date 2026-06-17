package com.wealthbuilder.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;


/**
 * Fails fast at startup if the built-in development JWT secret is still in use while a
 * real (non-development) profile is active. The dev default is intentionally public, so
 * signing tokens with it outside local development would let anyone forge a token —
 * including a moderator one.
 *
 * <p>Local runs are deliberately left untouched: an active {@code dev}/{@code local}/
 * {@code test} profile — or no active profile at all — is treated as development, so
 * {@code mvn spring-boot:run} keeps working out of the box.
 */
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

    /** Must match the dev fallback for {@code app.jwt.secret} in application.properties. */
    private static final String DEV_DEFAULT_SECRET = "dev-only-insecure-secret-change-me-0123456789abcdef";

    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test");

    private final AppProperties appProperties;

    private final Environment environment;

    @PostConstruct
    void rejectDevSecretOutsideDevelopment() {
        if (!isUsingDevDefaultSecret() || isDevelopmentProfile()) {
            return;
        }

        throw new IllegalStateException(
                "Refusing to start: the public development JWT secret is active under a non-development "
                        + "profile. Set the JWT_SECRET environment variable to a private value.");
    }

    private boolean isUsingDevDefaultSecret() {
        return DEV_DEFAULT_SECRET.equals(appProperties.getJwt().getSecret());
    }

    private boolean isDevelopmentProfile() {
        final String[] activeProfiles = environment.getActiveProfiles();

        if (activeProfiles.length == 0) {
            return true;
        }

        return Arrays
                .stream(activeProfiles)
                .anyMatch(DEV_PROFILES::contains);
    }
}
