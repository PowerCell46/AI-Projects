package com.peter_gerdzhikov.signal_flow_mail_service.exceptions;

/**
 * The mail send failed in a way that will never succeed on retry - an SMTP {@code 5xx} address
 * rejection, a malformed message, or an invalid address. Not retryable - dead-lettered immediately, and
 * the Redis claim is released so a later DLT replay can still reprocess it.
 */
public class PermanentMailDeliveryException extends RuntimeException {

    public PermanentMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
