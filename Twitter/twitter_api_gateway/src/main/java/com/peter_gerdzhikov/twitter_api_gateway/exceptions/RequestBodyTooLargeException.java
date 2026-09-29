package com.peter_gerdzhikov.twitter_api_gateway.exceptions;

public class RequestBodyTooLargeException extends RuntimeException {

    public static final String MESSAGE = "The request body is too large.";

    public RequestBodyTooLargeException() {
        super(MESSAGE);
    }
}
