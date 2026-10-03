package com.peter_gerdzhikov.twitter_timeline_service.exceptions;

public class TweetNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Tweet not found.";

    public TweetNotFoundException() {
        super(MESSAGE);
    }
}
