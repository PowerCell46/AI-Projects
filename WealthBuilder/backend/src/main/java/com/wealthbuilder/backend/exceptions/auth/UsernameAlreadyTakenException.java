package com.wealthbuilder.backend.exceptions.auth;


/**
 * Raised when registration is attempted with a username that already exists. Surfaced
 * as HTTP 409 by the global exception handler.
 */
public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String username) {
        super("Username already taken: " + username);
    }
}
