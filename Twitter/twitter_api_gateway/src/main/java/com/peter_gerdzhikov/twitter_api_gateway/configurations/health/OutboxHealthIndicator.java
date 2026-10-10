package com.peter_gerdzhikov.twitter_api_gateway.configurations.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reports {@code DEGRADED} while any outbox row is {@code FAILED}: an event that will never be published unless
 * someone requeues it. Not {@code DOWN}, which would fail the container health check and keep everything that waits
 * for a healthy gateway from starting.
 */
@Component
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final OutboxRepository outboxRepository;

    @Override
    public Health health() {
        long failedRows = outboxRepository.countByStatus(OutboxStatus.FAILED);

        if (failedRows == 0) {
            return Health.up().build();
        }

        return Health
                .status(DEGRADED)
                .withDetail("failedRows", failedRows)
                .build();
    }
}
