package com.wealthbuilder.backend.dtos.auth;

import lombok.Value;


/**
 * Returned by register and login: the bearer token the SPA stores and replays on every
 * subsequent request.
 */
@Value
public class AuthResponse {

    String token;

    public static AuthResponse of(String token) {
        return new AuthResponse(token);
    }
}
