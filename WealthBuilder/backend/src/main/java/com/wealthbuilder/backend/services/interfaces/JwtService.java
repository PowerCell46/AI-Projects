package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.entities.Role;
import com.wealthbuilder.backend.exceptions.InvalidTokenException;


/**
 * Issues and verifies HS256 JWT access tokens carrying the username (subject) and role.
 */
public interface JwtService {

    /**
     * Mints a signed token valid for the configured TTL.
     */
    String issueToken(String username, Role role);

    /**
     * Verifies the signature and expiry, returning the token's subject (username).
     *
     * @throws InvalidTokenException if the token is malformed, unsigned by us, or expired
     */
    String extractUsername(String token);
}
