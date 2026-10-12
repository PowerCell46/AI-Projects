package com.peter_gerdzhikov.twitter_timeline_service.configurations.health;

import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Reports {@code DOWN} while any Kafka listener container is stopped: without it the service answers healthy
 * while silently consuming nothing. {@code DOWN} turns {@code /actuator/health} to 503 and fails the container
 * health check, because a stopped listener never recovers on its own.
 */
@Component
@RequiredArgsConstructor
public class KafkaListenerHealthIndicator implements HealthIndicator {

    private final KafkaListenerEndpointRegistry registry;

    @Override
    public Health health() {
        List<String> stoppedListenerIds = registry
                .getAllListenerContainers()
                .stream()
                .filter(container -> !container.isRunning())
                .map(MessageListenerContainer::getListenerId)
                .toList();

        if (stoppedListenerIds.isEmpty()) {
            return Health.up().build();
        }

        return Health
                .down()
                .withDetail("stoppedListeners", stoppedListenerIds)
                .build();
    }
}
