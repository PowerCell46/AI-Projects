package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jwt.JWTClaimsSet;

import com.peter_gerdzhikov.twitter_api_gateway.configurations.security.SecurityConfiguration;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;

class TokenServiceImplTest {

    private static final Duration TTL = Duration.ofHours(1);

    private static final String SECRET = "unit-test-signing-secret-at-least-32-bytes-long";

    private JwtDecoder jwtDecoder;

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration((request, response, e) -> {
        }, (request, response, e) -> {
        });
        SecretKey secretKey = securityConfiguration.jwtSecretKey(SECRET);
        JwtEncoder jwtEncoder = securityConfiguration.jwtEncoder(secretKey);
        jwtDecoder = securityConfiguration.jwtDecoder(secretKey);
        tokenService = new TokenServiceImpl(TTL, jwtEncoder);
    }

    @Test
    void should_mint_a_token_carrying_the_expected_claims() {
        User user = newUser();

        Jwt jwt = jwtDecoder.decode(tokenService.mint(user));

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo(user.getUsername());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(TTL));
    }

    @Test
    void should_mint_a_token_without_a_role_claim() {
        Jwt jwt = jwtDecoder.decode(tokenService.mint(newUser()));

        assertThat(jwt.getClaims()).doesNotContainKey("role");
    }

    @Test
    void should_reject_an_expired_token() {
        Instant issuedAt = Instant.now().minusSeconds(7200);
        String token = signedWith(SECRET, TestJwts.validClaims()
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(3600))));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_signed_with_a_different_secret() {
        String token = signedWith("a-completely-different-signing-secret-that-is-long-enough", TestJwts.validClaims());

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_with_a_tampered_signature() {
        String token = TestJwts.withTamperedSignature(tokenService.mint(newUser()));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_missing_the_subject() {
        String token = signedWith(SECRET, TestJwts.validClaims().subject(null));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_whose_subject_is_not_a_uuid() {
        String token = signedWith(SECRET, TestJwts.validClaims().subject("not-a-uuid"));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_missing_the_username_claim() {
        String token = signedWith(SECRET, TestJwts.validClaims().claim("username", null));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_missing_the_email_claim() {
        String token = signedWith(SECRET, TestJwts.validClaims().claim("email", null));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_missing_the_expiry() {
        String token = signedWith(SECRET, TestJwts.validClaims().expirationTime(null));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String signedWith(String secret, JWTClaimsSet.Builder claims) {
        return TestJwts.sign(claims, secret);
    }

    private User newUser() {
        User user = User.builder()
                .username("token_user")
                .email("token_user@example.test")
                .password("hashed-password")
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
