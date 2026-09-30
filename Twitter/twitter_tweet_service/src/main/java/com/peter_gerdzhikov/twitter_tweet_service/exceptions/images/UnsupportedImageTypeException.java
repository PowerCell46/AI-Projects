package com.peter_gerdzhikov.twitter_tweet_service.exceptions.images;

public class UnsupportedImageTypeException extends RuntimeException {

    public static final String MESSAGE = "Unsupported image type.";

    public UnsupportedImageTypeException() {
        super(MESSAGE);
    }
}
