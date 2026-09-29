package com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth;

public class DuplicateEmailException extends RuntimeException {

    public static final String MESSAGE = "Email already registered.";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
