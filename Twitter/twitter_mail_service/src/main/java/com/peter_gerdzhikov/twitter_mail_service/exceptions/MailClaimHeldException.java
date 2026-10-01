package com.peter_gerdzhikov.twitter_mail_service.exceptions;

/**
 * Another consumer (or a crashed holder whose claim hasn't expired yet) already holds the inbox claim for this
 * email. Retryable - the record is re-offered to the error handler's backoff so an expired claim from a crashed
 * holder can be picked up on a later attempt instead of being acked away.
 */
public class MailClaimHeldException extends RuntimeException {

    public MailClaimHeldException(String message) {
        super(message);
    }
}
