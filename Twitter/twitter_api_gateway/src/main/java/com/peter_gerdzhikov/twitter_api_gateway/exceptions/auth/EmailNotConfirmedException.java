package com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth;

public class EmailNotConfirmedException extends RuntimeException {

    public static final String MESSAGE = "Please confirm your email first.";

    public EmailNotConfirmedException() {
        super(MESSAGE);
    }
}
