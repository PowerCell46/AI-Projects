package com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth;

public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Invalid credentials.";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
