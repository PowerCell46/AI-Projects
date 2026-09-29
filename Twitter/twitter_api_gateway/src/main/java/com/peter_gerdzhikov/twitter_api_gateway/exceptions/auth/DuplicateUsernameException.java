package com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth;

public class DuplicateUsernameException extends RuntimeException {

    public static final String MESSAGE = "Username already taken.";

    public DuplicateUsernameException() {
        super(MESSAGE);
    }
}
