package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

public interface EventValidationService {

    /**
     * Bean-validates a Kafka event. {@code eventLabel} names the event in the log line and the exception, so it
     * may carry ids but never the tweet's text.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException
     *         on any violation - not retryable
     */
    void validate(Object event, String eventLabel);
}
