package com.peter_gerdzhikov.twitter_mail_service.exceptions;

/**
 * A Kafka event failed bean validation. Not retryable - the record is malformed, not transiently
 * unprocessable - so the error handler sends it straight to the dead-letter topic.
 */
public class InvalidMailEventException extends RuntimeException {

    public InvalidMailEventException(String message) {
        super(message);
    }
}
