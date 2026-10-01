package com.peter_gerdzhikov.twitter_mail_service.exceptions;

/**
 * The mail send failed in a way that may succeed on retry - a connect failure, timeout, SMTP {@code 4xx}
 * reply, or auth failure. Retryable. Deliberately has no cause, since an SMTP reply routinely echoes the
 * rejected recipient address.
 */
public class TransientMailDeliveryException extends RuntimeException {

    public TransientMailDeliveryException(String message) {
        super(message);
    }
}
