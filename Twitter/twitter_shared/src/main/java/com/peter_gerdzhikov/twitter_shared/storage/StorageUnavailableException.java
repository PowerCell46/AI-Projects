package com.peter_gerdzhikov.twitter_shared.storage;

public class StorageUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Storage unavailable.";

    public StorageUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
