package com.peter_gerdzhikov.twitter_shared.outbox;

/**
 * Publishes one batch of {@code PENDING} entries, oldest first. The batch stops at the first entry Kafka
 * could not take, and that entry stays {@code PENDING} with its attempts untouched, so an outage of any
 * length loses nothing. An entry Kafka refuses for its own content counts an attempt, becomes
 * {@code FAILED} at the limit, and the batch goes on past it.
 *
 * @return how many entries reached Kafka and were deleted
 */
public interface OutboxPublisherService {

    int publishPending();
}
