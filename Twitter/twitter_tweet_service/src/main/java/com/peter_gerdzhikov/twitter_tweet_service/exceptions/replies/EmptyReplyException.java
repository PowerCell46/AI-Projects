package com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies;

public class EmptyReplyException extends RuntimeException {

    public static final String MESSAGE = "A reply needs text.";

    public EmptyReplyException() {
        super(MESSAGE);
    }
}
