package com.peter_gerdzhikov.twitter_timeline_service.exceptions.views;

public class InvalidTweetIdsException extends RuntimeException {

    public InvalidTweetIdsException(int maxSize) {
        super("Between 1 and %d tweet ids are required, none of them null.".formatted(maxSize));
    }
}
