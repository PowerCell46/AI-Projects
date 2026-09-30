package com.peter_gerdzhikov.twitter_tweet_service.exceptions;

public class InvalidCallerIdentityException extends RuntimeException {

    public static final String MESSAGE = "Missing or invalid caller identity.";

    public InvalidCallerIdentityException() {
        super(MESSAGE);
    }
}
