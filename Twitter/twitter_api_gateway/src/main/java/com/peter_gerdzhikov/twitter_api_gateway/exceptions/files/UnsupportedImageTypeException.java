package com.peter_gerdzhikov.twitter_api_gateway.exceptions.files;

public class UnsupportedImageTypeException extends RuntimeException {

    public static final String MESSAGE = "Unsupported image type.";

    public UnsupportedImageTypeException() {
        super(MESSAGE);
    }
}
