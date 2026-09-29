package com.peter_gerdzhikov.twitter_api_gateway.exceptions.files;

public class EmptyUploadException extends RuntimeException {

    public static final String MESSAGE = "The uploaded file is empty.";

    public EmptyUploadException() {
        super(MESSAGE);
    }
}
