package com.peter_gerdzhikov.twitter_api_gateway.exceptions.files;

public class FileNotFoundException extends RuntimeException {

    public static final String MESSAGE = "File not found.";

    public FileNotFoundException() {
        super(MESSAGE);
    }
}
