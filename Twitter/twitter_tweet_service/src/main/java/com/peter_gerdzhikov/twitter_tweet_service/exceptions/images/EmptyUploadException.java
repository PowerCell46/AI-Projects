package com.peter_gerdzhikov.twitter_tweet_service.exceptions.images;

public class EmptyUploadException extends RuntimeException {

    public static final String MESSAGE = "The uploaded file is empty.";

    public EmptyUploadException() {
        super(MESSAGE);
    }
}
