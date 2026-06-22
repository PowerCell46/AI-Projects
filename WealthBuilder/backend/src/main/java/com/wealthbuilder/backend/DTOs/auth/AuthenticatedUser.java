package com.wealthbuilder.backend.DTOs.auth;

import lombok.Value;


/**
 * Result of a successful register/login: the freshly minted JWT plus the current-user snapshot.
 * The controller puts the token into an httpOnly cookie and returns only the {@link #user} in the
 * body, so the token never reaches JavaScript.
 */
@Value
public class AuthenticatedUser {

    String token;

    CurrentUserResponse user;

    public static AuthenticatedUser of(String token, CurrentUserResponse user) {
        return new AuthenticatedUser(token, user);
    }
}
