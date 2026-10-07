package com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails;

public class AuthorNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Author not found.";

    public AuthorNotFoundException() {
        super(MESSAGE);
    }
}
