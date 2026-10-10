package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

public interface OutboxPublisherService {

    /**
     * Publishes one batch of {@code PENDING} messages, oldest first. The batch stops at the first message Kafka could
     * not take, and that message stays {@code PENDING} with its attempts untouched, so an outage of any length loses
     * nothing. A message Kafka refuses for its own content counts an attempt, becomes {@code FAILED} at the limit, and
     * the batch goes on past it.
     *
     * @return how many messages reached Kafka and were deleted
     */
    int publishPending();
}
