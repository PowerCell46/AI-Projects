package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox;

public interface OutboxPublisherService {

    /**
     * Publishes one batch of {@code PENDING} rows, oldest first. The batch stops at the first row Kafka could not
     * take, and that row stays {@code PENDING} with its attempts untouched, so an outage of any length loses nothing.
     * A row Kafka refuses for its own content counts an attempt, becomes {@code FAILED} at the limit, and the batch
     * goes on past it.
     *
     * @return how many rows reached Kafka and were deleted
     */
    int publishPending();
}
