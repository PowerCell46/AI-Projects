package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access;

public class TweetImageNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Image not found.";

    public TweetImageNotFoundException() {
        super(MESSAGE);
    }
}
