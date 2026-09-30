package com.peter_gerdzhikov.twitter_api_gateway.support;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints tokens by hand so a test can break exactly one thing - the signature, the expiry or a single
 * claim - which {@code TokenService} would never produce.
 */
public final class TestJwts {

    public static final String USERNAME = "jwt_user";

    public static final String EMAIL = "jwt_user@example.test";

    private TestJwts() {
    }

    public static JWTClaimsSet.Builder validClaims() {
        Instant issuedAt = Instant.now();

        return new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("username", USERNAME)
                .claim("email", EMAIL)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(3600)));
    }

    public static String sign(JWTClaimsSet.Builder claims, String secret) {
        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            signedJwt.sign(new MACSigner(secret));
            return signedJwt.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the test token.", e);
        }
    }

    /**
     * Flips the first character of the signature, not the last: the last base64url character of a
     * 32-byte HS256 signature carries padding bits, so changing it can leave the decoded bytes intact.
     */
    public static String withTamperedSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        char flipped = first == 'A' ? 'B' : 'A';

        return token.substring(0, signatureStart) + flipped + token.substring(signatureStart + 1);
    }
}
