package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets;

public class TweetNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Tweet not found.";

    public TweetNotFoundException() {
        super(MESSAGE);
    }
}
