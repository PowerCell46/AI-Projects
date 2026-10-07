package com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging;

public class InvalidCursorException extends RuntimeException {

    public static final String MESSAGE = "Invalid cursor.";

    public InvalidCursorException() {
        super(MESSAGE);
    }
}
