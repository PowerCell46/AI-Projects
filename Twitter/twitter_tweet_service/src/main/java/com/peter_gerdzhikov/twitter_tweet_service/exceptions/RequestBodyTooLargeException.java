package com.peter_gerdzhikov.twitter_tweet_service.exceptions;

public class RequestBodyTooLargeException extends RuntimeException {

    public static final String MESSAGE = "The request body is too large.";

    public RequestBodyTooLargeException() {
        super(MESSAGE);
    }
}
