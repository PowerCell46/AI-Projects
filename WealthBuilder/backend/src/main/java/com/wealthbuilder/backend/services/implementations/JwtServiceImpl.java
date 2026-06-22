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
 * Nimbus-backed HS256 implementation. Verification re-checks the signature, algorithm header,
 * issuer/audience binding, and expiry (with clock-skew tolerance) so tampered, cross-deployment,
 * and stale tokens are all rejected.
 */
@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private static final String VERSION_CLAIM = "ver";

    private static final String TOKEN_ISSUER = "wealthbuilder";

    private static final String TOKEN_AUDIENCE = "wealthbuilder";

    private static final int MIN_SECRET_BYTES = 32;

    // Safety ceiling so a misconfigured JWT_TTL (e.g. PT720H) can't mint month-long tokens that
    // stay valid long after a logout or password change.
    private static final Duration MAX_TTL = Duration.ofHours(24);

    // Tolerate up to 30 s of clock drift between the issuer and verifier. Without this, a token
    // verified on a machine whose clock runs slightly fast can be rejected at the exact boundary.
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

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
                .issuer(TOKEN_ISSUER)
                .audience(TOKEN_AUDIENCE)
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
            ensureAlgorithmIsHS256(parsedToken);
            verifySignature(parsedToken);
            final JWTClaimsSet claims = parsedToken.getJWTClaimsSet();
            ensureIssuerAndAudience(claims);
            ensureNotExpired(claims);

            return new TokenClaims(claims.getSubject(), tokenVersionOf(claims));

        } catch (ParseException ex) {
            throw new InvalidTokenException("Malformed JWT", ex);
        }
    }

    /**
     * Rejects any token whose header declares an algorithm other than HS256. {@code MACVerifier}
     * only supports HMAC families, but explicitly checking here is cheap defense-in-depth against
     * algorithm-confusion attacks before the key material is even touched.
     */
    private void ensureAlgorithmIsHS256(SignedJWT token) {
        if (!JWSAlgorithm.HS256.equals(token.getHeader().getAlgorithm())) {
            throw new InvalidTokenException(
                    "Unexpected JWT algorithm: " + token.getHeader().getAlgorithm());
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

    /**
     * Checks issuer and audience so a token signed with the same secret by a different deployment
     * (e.g. a staging instance sharing the secret) cannot be used here.
     */
    private void ensureIssuerAndAudience(JWTClaimsSet claims) {
        if (!TOKEN_ISSUER.equals(claims.getIssuer())) {
            throw new InvalidTokenException("JWT issuer mismatch");
        }

        if (!claims.getAudience().contains(TOKEN_AUDIENCE)) {
            throw new InvalidTokenException("JWT audience mismatch");
        }
    }

    private void ensureNotExpired(JWTClaimsSet claims) {
        final Date expiry = claims.getExpirationTime();
        if (expiry == null || expiry.toInstant().isBefore(Instant.now().minus(CLOCK_SKEW))) {
            throw new InvalidTokenException("JWT is expired");
        }
    }

    /**
     * Reads the version claim. Rejects tokens that omit it — all tokens issued by this service
     * carry the claim, so its absence means the token is either tampered or from a foreign issuer.
     */
    private int tokenVersionOf(JWTClaimsSet claims) throws ParseException {
        final Integer version = claims.getIntegerClaim(VERSION_CLAIM);

        if (version == null) {
            throw new InvalidTokenException("JWT is missing the required '" + VERSION_CLAIM + "' claim");
        }

        return version;
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
}
