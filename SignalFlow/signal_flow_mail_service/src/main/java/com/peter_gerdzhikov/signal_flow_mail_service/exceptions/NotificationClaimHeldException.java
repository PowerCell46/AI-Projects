package com.peter_gerdzhikov.signal_flow_mail_service.exceptions;

/**
 * Another consumer (or a crashed holder whose claim hasn't expired yet) already holds the Redis inbox
 * claim for this {@code (newsId, userId)} pair. Retryable - the record is re-offered to the error
 * handler's backoff so an expired claim from a crashed holder can be picked up on a later attempt
 * instead of being acked away.
 */
public class NotificationClaimHeldException extends RuntimeException {

    public NotificationClaimHeldException(String message) {
        super(message);
    }
}
