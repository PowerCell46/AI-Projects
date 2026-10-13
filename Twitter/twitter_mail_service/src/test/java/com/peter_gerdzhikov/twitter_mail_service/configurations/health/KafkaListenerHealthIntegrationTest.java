package com.peter_gerdzhikov.twitter_mail_service.configurations.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import com.peter_gerdzhikov.twitter_mail_service.support.base.AbstractNotificationE2ETest;

class KafkaListenerHealthIntegrationTest extends AbstractNotificationE2ETest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaListenerHealthIndicator indicator;

    @Test
    void should_report_up_when_all_listener_containers_are_running() {
        assertThat(registry.getAllListenerContainers()).isNotEmpty();

        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Health health = indicator.health();

                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                });
    }

    @Test
    void should_report_down_when_a_listener_container_is_stopped() {
        MessageListenerContainer container = registry
                .getAllListenerContainers()
                .iterator()
                .next();

        container.stop();

        try {
            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        Health health = indicator.health();

                        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    });

        } finally {
            container.start();

            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(200))
                    .until(container::isRunning);
        }
    }
}
