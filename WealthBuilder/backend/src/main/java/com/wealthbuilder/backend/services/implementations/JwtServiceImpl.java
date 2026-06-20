package com.wealthbuilder.backend.services.implementations;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wealthbuilder.backend.config.AppProperties;
import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.exceptions.auth.InvalidTokenException;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;


/**
 * Nimbus-backed HS256 implementation. Verification re-checks the signature and expiry so
 * a tampered or stale token is rejected.
 */
@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private static final String ROLE_CLAIM = "role";

    private static final int MIN_SECRET_BYTES = 32;

    private final byte[] signingKey;

    private final Duration tokenTtl;

    public JwtServiceImpl(AppProperties appProperties) {
        this.signingKey = appProperties
                .getJwt()
                .getSecret()
                .getBytes(StandardCharsets.UTF_8);
        this.tokenTtl = appProperties
                .getJwt()
                .getTtl();

        ensureSecretIsStrongEnough();
    }

    /**
     * Fails fast at startup if the configured secret is too short for HS256, which requires a
     * key of at least 256 bits. Without this the app boots fine and only blows up on the first
     * login attempt.
     */
    private void ensureSecretIsStrongEnough() {
        if (this.signingKey.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes (256 bits) for HS256; got " + this.signingKey.length);
        }
    }

    @Override
    public String issueToken(String username, Role role) {
        final Instant now = Instant.now();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(tokenTtl)))
                .build();

        log.debug("Issuing JWT for '{}' (role {}, valid {})", username, role, tokenTtl);

        return sign(claims);
    }

    @Override
    public String extractUsername(String token) {
        try {
            final SignedJWT parsedToken = SignedJWT.parse(token);
            verifySignature(parsedToken);
            final JWTClaimsSet claims = parsedToken.getJWTClaimsSet();
            ensureNotExpired(claims);

            return claims.getSubject();
        } catch (ParseException ex) {
            throw new InvalidTokenException("Malformed JWT", ex);
        }
    }

    private String sign(JWTClaimsSet claims) {
        try {
            final SignedJWT signedToken = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedToken.sign(new MACSigner(signingKey));

            return signedToken.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private void verifySignature(SignedJWT token) {
        try {
            if (!token.verify(new MACVerifier(signingKey))) {
                throw new InvalidTokenException("JWT signature verification failed");
            }
        } catch (JOSEException ex) {
            throw new InvalidTokenException("Unable to verify JWT signature", ex);
        }
    }

    private void ensureNotExpired(JWTClaimsSet claims) {
        final Date expiry = claims.getExpirationTime();
        if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
            throw new InvalidTokenException("JWT is expired");
        }
    }
}
