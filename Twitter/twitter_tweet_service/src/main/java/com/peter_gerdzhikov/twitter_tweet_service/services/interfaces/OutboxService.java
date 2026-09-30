package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

public interface OutboxService {

    /**
     * Serialises the payload to JSON once and queues it as a {@code PENDING} message. Joins the caller's
     * transaction, so the message exists exactly when the tweet change that produced it does.
     */
    void enqueue(String topic, String messageKey, Object payload);
}
