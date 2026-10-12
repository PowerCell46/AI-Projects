package com.peter_gerdzhikov.twitter_timeline_service.configurations.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;

class KafkaListenerHealthIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaListenerHealthIndicator indicator;

    @Test
    void should_answer_200_with_up_when_all_listener_containers_are_running() throws Exception {
        assertThat(registry.getAllListenerContainers()).isNotEmpty();

        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Health health = indicator.health();

                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                });

        mockMvc
                .perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void should_answer_503_with_down_when_a_listener_container_is_stopped() throws Exception {
        MessageListenerContainer container = registry
                .getAllListenerContainers()
                .iterator()
                .next();

        container.stop();

        try {
            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> mockMvc
                            .perform(get("/actuator/health"))
                            .andExpect(status().isServiceUnavailable())
                            .andExpect(jsonPath("$.status").value("DOWN")));

        } finally {
            container.start();

            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(200))
                    .until(container::isRunning);
        }
    }
}
