package com.wealthbuilder.backend.DTOs.auth;

import lombok.Value;


/**
 * The verified contents of a JWT: the subject (username) and the token-version claim. The auth
 * filter checks the version against the user's current one so revoked tokens are rejected.
 */
@Value
public class TokenClaims {

    String username;

    int tokenVersion;
}
