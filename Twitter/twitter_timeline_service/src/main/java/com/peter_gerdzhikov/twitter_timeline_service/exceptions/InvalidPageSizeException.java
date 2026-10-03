package com.peter_gerdzhikov.twitter_timeline_service.exceptions;

public class InvalidPageSizeException extends RuntimeException {

    public InvalidPageSizeException(int minSize, int maxSize) {
        super("Page size must be between %d and %d.".formatted(minSize, maxSize));
    }
}
