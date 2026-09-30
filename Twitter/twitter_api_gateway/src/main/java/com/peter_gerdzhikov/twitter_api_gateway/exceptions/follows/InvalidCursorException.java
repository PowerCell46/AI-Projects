package com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows;

public class InvalidCursorException extends RuntimeException {

    public static final String MESSAGE = "Invalid cursor.";

    public InvalidCursorException() {
        super(MESSAGE);
    }
}
