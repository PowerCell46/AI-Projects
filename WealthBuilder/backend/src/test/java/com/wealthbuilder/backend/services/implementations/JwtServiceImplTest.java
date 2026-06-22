package com.wealthbuilder.backend.services.implementations;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wealthbuilder.backend.config.AppProperties;
import com.wealthbuilder.backend.exceptions.auth.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;


/**
 * Pure unit test for the Nimbus HS256 token service. No Spring context: the service is
 * constructed directly from a hand-built {@link AppProperties}.
 */
class JwtServiceImplTest {

    private static final String SIGNING_SECRET = "unit-test-secret-key-that-is-at-least-256-bits-long-0123456789";

    private static final String OTHER_SECRET = "a-totally-different-secret-key-also-at-least-256-bits-long-9876";

    private static final String USERNAME = "alice";

    private static final int TOKEN_VERSION = 3;

    private static final Duration TTL = Duration.ofMinutes(30);

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(propertiesWithSecret(SIGNING_SECRET, TTL));
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        void should_RoundTripSubject_When_TokenIssuedThenRead() {
            final String token = jwtService.issueToken(USERNAME, TOKEN_VERSION);

            assertThat(jwtService.verify(token).getUsername()).isEqualTo(USERNAME);
        }

        @Test
        void should_RoundTripTokenVersion_When_TokenIssuedThenRead() {
            final String token = jwtService.issueToken(USERNAME, TOKEN_VERSION);

            assertThat(jwtService.verify(token).getTokenVersion()).isEqualTo(TOKEN_VERSION);
        }
    }

    @Nested
    @DisplayName("TTL bound")
    class TtlBound {

        @Test
        void should_FailFast_When_TtlExceedsTheCeiling() {
            assertThatThrownBy(() -> new JwtServiceImpl(propertiesWithSecret(SIGNING_SECRET, Duration.ofHours(25))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void should_FailFast_When_TtlIsNotPositive() {
            assertThatThrownBy(() -> new JwtServiceImpl(propertiesWithSecret(SIGNING_SECRET, Duration.ZERO)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Rejection scenarios")
    class Rejection {

        @Test
        void should_ThrowInvalidToken_When_SignatureTampered() {
            final String token = jwtService.issueToken(USERNAME, TOKEN_VERSION);
            final String tampered = tamperSignature(token);

            assertThatThrownBy(() -> jwtService.verify(tampered))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void should_ThrowExpired_When_TokenAlreadyExpired() throws Exception {
            final String expiredToken = signTokenExpiringAt(Instant.now().minusSeconds(60));

            assertThatThrownBy(() -> jwtService.verify(expiredToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessage("JWT is expired");
        }

        @Test
        void should_ThrowMalformed_When_TokenIsGarbage() {
            assertThatThrownBy(() -> jwtService.verify("not-a-real-jwt"))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessage("Malformed JWT");
        }

        @Test
        void should_ThrowInvalidToken_When_SignedWithDifferentKey() {
            final JwtServiceImpl foreignService =
                    new JwtServiceImpl(propertiesWithSecret(OTHER_SECRET, TTL));
            final String foreignToken = foreignService.issueToken(USERNAME, TOKEN_VERSION);

            final Throwable thrown = catchThrowable(() -> jwtService.verify(foreignToken));

            assertThat(thrown).isInstanceOf(InvalidTokenException.class);
        }
    }

    private static AppProperties propertiesWithSecret(String secret, Duration ttl) {
        final AppProperties.Jwt jwt = new AppProperties.Jwt(secret, ttl);
        final AppProperties.Moderator moderator = new AppProperties.Moderator("mod", "pw");

        return new AppProperties(java.util.List.of("http://localhost"), jwt, null, moderator);
    }

    /**
     * Flips the first character of the signature segment. Unlike the trailing character —
     * whose low bits are unused base64url padding and may decode to identical bytes — the
     * leading character's bits are always significant, so the signature reliably changes.
     */
    private static String tamperSignature(String token) {
        final int signatureStart = token.lastIndexOf('.') + 1;
        final char first = token.charAt(signatureStart);
        final char replacement = (first == 'A') ? 'B' : 'A';

        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static String signTokenExpiringAt(Instant expiry) throws Exception {
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(USERNAME)
                .issueTime(Date.from(expiry.minusSeconds(600)))
                .expirationTime(Date.from(expiry))
                .build();
        final SignedJWT signedToken = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedToken.sign(new MACSigner(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8)));

        return signedToken.serialize();
    }
}
