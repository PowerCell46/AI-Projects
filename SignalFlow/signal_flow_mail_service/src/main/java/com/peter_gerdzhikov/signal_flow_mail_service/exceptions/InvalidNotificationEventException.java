package com.peter_gerdzhikov.signal_flow_mail_service.exceptions;

/**
 * A {@link com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO} failed
 * bean validation. Not retryable - the record is malformed, not transiently unprocessable - so the error
 * handler sends it straight to the dead-letter topic.
 */
public class InvalidNotificationEventException extends RuntimeException {

    public InvalidNotificationEventException(String message) {
        super(message);
    }
}
