package com.peter_gerdzhikov.twitter_timeline_service.exceptions;

public class InvalidCursorException extends RuntimeException {

    public static final String MESSAGE = "Invalid cursor.";

    public InvalidCursorException() {
        super(MESSAGE);
    }
}
