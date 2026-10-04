package com.peter_gerdzhikov.twitter_mail_service.services.interfaces;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;

public interface MailEventValidationService {

    /**
     * Bean-validates a Kafka event. {@code eventLabel} names the event in the log line and the exception, so it
     * may carry ids but never the address or a token.
     *
     * @throws InvalidMailEventException on any violation - not retryable
     */
    void validate(Object event, String eventLabel);
}
