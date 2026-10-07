package com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging;

public class InvalidPageSizeException extends RuntimeException {

    public InvalidPageSizeException(int minSize, int maxSize) {
        super("Page size must be between %d and %d.".formatted(minSize, maxSize));
    }
}
