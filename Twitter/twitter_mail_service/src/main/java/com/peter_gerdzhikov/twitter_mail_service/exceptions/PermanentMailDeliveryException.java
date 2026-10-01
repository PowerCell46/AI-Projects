package com.peter_gerdzhikov.twitter_mail_service.exceptions;

/**
 * The mail send failed in a way that will never succeed on retry - an SMTP {@code 5xx} rejection, a malformed
 * message, or an invalid address. Not retryable: dead-lettered immediately. Deliberately has no cause, since
 * an SMTP reply routinely echoes the rejected recipient address.
 */
public class PermanentMailDeliveryException extends RuntimeException {

    public PermanentMailDeliveryException(String message) {
        super(message);
    }
}
