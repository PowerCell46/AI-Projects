package com.peter_gerdzhikov.twitter_tweet_service.configurations.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reports {@code DEGRADED} while any outbox message is {@code FAILED}: an event that will never be published unless
 * someone requeues it. Not {@code DOWN}, which would fail the container health check and keep everything that waits
 * for a healthy tweet service from starting.
 */
@Component
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final OutboxMessageRepository outboxMessageRepository;

    @Override
    public Health health() {
        long failedMessages = outboxMessageRepository.countByStatus(OutboxStatus.FAILED);

        if (failedMessages == 0) {
            return Health.up().build();
        }

        return Health
                .status(DEGRADED)
                .withDetail("failedMessages", failedMessages)
                .build();
    }
}
