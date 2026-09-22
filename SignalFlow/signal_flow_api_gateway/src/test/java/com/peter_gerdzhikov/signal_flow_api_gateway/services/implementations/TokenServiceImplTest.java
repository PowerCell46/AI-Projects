package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.peter_gerdzhikov.signal_flow_api_gateway.configurations.SecurityConfiguration;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;

class TokenServiceImplTest {

    private static final String SECRET = "unit-test-signing-secret-at-least-32-bytes-long";
    private static final Duration TTL = Duration.ofHours(1);
    private static final String EMAIL = "user@example.com";

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
        tokenService = new TokenServiceImpl(jwtEncoder, TTL);
    }

    @Test
    void should_mint_a_token_carrying_the_expected_claims() {
        User user = newUser();

        Jwt jwt = jwtDecoder.decode(tokenService.mint(user));

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getClaimAsString("role")).isEqualTo(user.getRole().name());
        assertThat(jwt.getClaimAsInstant("createdAt")).isEqualTo(user.getCreatedAt());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(TTL));
    }

    @Test
    void should_decode_a_valid_token() {
        String token = tokenService.mint(newUser());

        assertThat(jwtDecoder.decode(token)).isNotNull();
    }

    @Test
    void should_reject_an_expired_token() throws JOSEException {
        String token = mintRawToken(SECRET, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), true);

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_signed_with_a_different_secret() throws JOSEException {
        String token = mintRawToken(
                "a-completely-different-signing-secret-that-is-long-enough", Instant.now(), Instant.now().plusSeconds(3600), true);

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_tampered_token() {
        String[] parts = tokenService.mint(newUser()).split("\\.");
        char lastPayloadChar = parts[1].charAt(parts[1].length() - 1);
        char flipped = lastPayloadChar == 'A' ? 'B' : 'A';
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) + flipped;
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtDecoder.decode(tamperedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void should_reject_a_token_missing_required_claims() throws JOSEException {
        String token = mintRawToken(SECRET, Instant.now(), Instant.now().plusSeconds(3600), false);

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private User newUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private String mintRawToken(String secret, Instant issuedAt, Instant expiresAt, boolean includeSubject) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .claim("email", EMAIL)
                .claim("role", Role.USER.name())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        if (includeSubject) {
            claims.subject(UUID.randomUUID().toString());
        }

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        signedJwt.sign(new MACSigner(secret));
        return signedJwt.serialize();
    }
}
