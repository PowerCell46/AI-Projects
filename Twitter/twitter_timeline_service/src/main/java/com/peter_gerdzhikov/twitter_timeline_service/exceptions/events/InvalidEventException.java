package com.peter_gerdzhikov.twitter_timeline_service.exceptions.events;

/**
 * A Kafka event failed bean validation. Not retryable - the record is malformed, not transiently
 * unprocessable - so the error handler sends it straight to the dead-letter topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
