package com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies;

public class ReplyNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Reply not found.";

    public ReplyNotFoundException() {
        super(MESSAGE);
    }
}
