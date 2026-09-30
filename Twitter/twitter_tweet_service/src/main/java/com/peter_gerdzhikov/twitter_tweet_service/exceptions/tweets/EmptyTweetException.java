package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets;

public class EmptyTweetException extends RuntimeException {

    public static final String MESSAGE = "A tweet needs text or an image.";

    public EmptyTweetException() {
        super(MESSAGE);
    }
}
