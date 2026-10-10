package com.peter_gerdzhikov.twitter_api_gateway.exceptions.locks;

public class AdvisoryLockFailedException extends RuntimeException {

    public AdvisoryLockFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
