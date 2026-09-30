package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

public interface OutboxPublisherService {

    /**
     * Publishes one batch of {@code PENDING} messages, oldest first.
     *
     * @return how many messages reached Kafka and were deleted
     */
    int publishPending();
}
