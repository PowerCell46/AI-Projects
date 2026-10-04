package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox;

public interface OutboxPublisherService {

    /**
     * Publishes one batch of {@code PENDING} rows, oldest first.
     *
     * @return how many rows reached Kafka and were deleted
     */
    int publishPending();
}
