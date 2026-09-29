package com.peter_gerdzhikov.twitter_api_gateway.exceptions.files;

public class StorageUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Storage unavailable.";

    public StorageUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
