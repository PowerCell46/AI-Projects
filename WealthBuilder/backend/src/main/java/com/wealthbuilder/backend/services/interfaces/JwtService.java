package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.DTOs.auth.TokenClaims;
import com.wealthbuilder.backend.exceptions.auth.InvalidTokenException;


/**
 * Issues and verifies HS256 JWT access tokens carrying the username (subject) and the token
 * version used for revocation. The role is not carried — authorities are reloaded from the DB
 * per request, so the token never decides authorization.
 */
public interface JwtService {

    /**
     * Mints a signed token valid for the configured TTL, stamped with the given token version.
     */
    String issueToken(String username, int tokenVersion);

    /**
     * Verifies the signature and expiry, returning the token's subject and version claim.
     *
     * @throws InvalidTokenException if the token is malformed, unsigned by us, or expired
     */
    TokenClaims verify(String token);
}
