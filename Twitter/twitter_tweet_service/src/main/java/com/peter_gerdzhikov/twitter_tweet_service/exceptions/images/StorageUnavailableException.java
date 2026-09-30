package com.peter_gerdzhikov.twitter_tweet_service.exceptions.images;

public class StorageUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Storage unavailable.";

    public StorageUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
