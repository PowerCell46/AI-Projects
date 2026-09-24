package com.peter_gerdzhikov.signal_flow_mail_service.exceptions;

/**
 * The mail send failed in a way that may succeed on retry - a connect failure, timeout, SMTP {@code 4xx}
 * reply, or auth failure. Retryable - the Redis claim is released so a later attempt can re-claim.
 */
public class TransientMailDeliveryException extends RuntimeException {

    public TransientMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
