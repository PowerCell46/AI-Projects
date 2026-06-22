package com.wealthbuilder.backend.services.implementations;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wealthbuilder.backend.DTOs.auth.TokenClaims;
import com.wealthbuilder.backend.config.AppProperties;
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

    private static final String VERSION_CLAIM = "ver";

    private static final int MIN_SECRET_BYTES = 32;

    // Safety ceiling so a misconfigured JWT_TTL (e.g. PT720H) can't mint month-long tokens that
    // stay valid long after a logout or password change. Keep the configured value well below this.
    private static final Duration MAX_TTL = Duration.ofHours(24);

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
        ensureTtlWithinBound();
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

    /** Fails fast if the TTL is missing, non-positive, or longer than the {@link #MAX_TTL} ceiling. */
    private void ensureTtlWithinBound() {
        if (this.tokenTtl == null || this.tokenTtl.isZero() || this.tokenTtl.isNegative()
                || this.tokenTtl.compareTo(MAX_TTL) > 0) {
            throw new IllegalStateException(
                    "app.jwt.ttl must be positive and at most " + MAX_TTL + "; got " + this.tokenTtl);
        }
    }

    @Override
    public String issueToken(String username, int tokenVersion) {
        final Instant now = Instant.now();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .claim(VERSION_CLAIM, tokenVersion)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(tokenTtl)))
                .build();

        log.debug("Issuing JWT for '{}' (version {}, valid {})", username, tokenVersion, tokenTtl);

        return sign(claims);
    }

    @Override
    public TokenClaims verify(String token) {
        try {
            final SignedJWT parsedToken = SignedJWT.parse(token);
            verifySignature(parsedToken);
            final JWTClaimsSet claims = parsedToken.getJWTClaimsSet();
            ensureNotExpired(claims);

            return new TokenClaims(claims.getSubject(), tokenVersionOf(claims));

        } catch (ParseException ex) {
            throw new InvalidTokenException("Malformed JWT", ex);
        }
    }

    /** Reads the version claim, treating a token issued without one (legacy) as version 0. */
    private int tokenVersionOf(JWTClaimsSet claims) throws ParseException {
        final Integer version = claims.getIntegerClaim(VERSION_CLAIM);

        return version == null ? 0 : version;
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
