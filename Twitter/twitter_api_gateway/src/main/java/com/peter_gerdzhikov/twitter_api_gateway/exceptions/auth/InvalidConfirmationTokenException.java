package com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth;

public class InvalidConfirmationTokenException extends RuntimeException {

    public static final String MESSAGE = "Invalid or expired confirmation token.";

    public InvalidConfirmationTokenException() {
        super(MESSAGE);
    }
}
