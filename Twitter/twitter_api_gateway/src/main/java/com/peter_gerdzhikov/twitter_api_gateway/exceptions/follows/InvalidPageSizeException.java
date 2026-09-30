package com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows;

public class InvalidPageSizeException extends RuntimeException {

    public static final String MESSAGE = "Page size must be between 1 and 100.";

    public InvalidPageSizeException() {
        super(MESSAGE);
    }
}
