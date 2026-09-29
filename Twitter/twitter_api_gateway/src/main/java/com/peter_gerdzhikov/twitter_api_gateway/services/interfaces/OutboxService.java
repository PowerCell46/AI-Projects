package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces;

public interface OutboxService {

    /**
     * Serialises the payload to JSON once and queues it as a {@code PENDING} row. Joins the caller's
     * transaction, so the row exists exactly when the business change that produced it does.
     */
    void enqueue(String topic, String messageKey, Object payload);
}
