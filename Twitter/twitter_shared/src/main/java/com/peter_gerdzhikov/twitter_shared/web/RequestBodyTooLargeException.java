package com.peter_gerdzhikov.twitter_shared.web;

public class RequestBodyTooLargeException extends RuntimeException {

    public static final String MESSAGE = "The request body is too large.";

    public RequestBodyTooLargeException() {
        super(MESSAGE);
    }
}
