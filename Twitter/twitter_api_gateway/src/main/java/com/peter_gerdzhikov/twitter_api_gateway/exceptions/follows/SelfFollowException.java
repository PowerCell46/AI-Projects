package com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows;

public class SelfFollowException extends RuntimeException {

    public static final String MESSAGE = "You cannot follow yourself.";

    public SelfFollowException() {
        super(MESSAGE);
    }
}
