package com.peter_gerdzhikov.twitter_mail_service.configurations.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class KafkaListenerHealthIndicatorTest {

    @Mock
    private MessageListenerContainer container;

    @Mock
    private KafkaListenerEndpointRegistry registry;

    @InjectMocks
    private KafkaListenerHealthIndicator indicator;

    @Test
    void should_report_up_when_all_listener_containers_are_running() {
        when(container.isRunning()).thenReturn(true);
        when(registry.getAllListenerContainers()).thenReturn(List.of(container));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_report_down_with_the_stopped_ids_when_a_listener_container_is_stopped() {
        when(container.isRunning()).thenReturn(false);
        when(container.getListenerId()).thenReturn("user.followed-0");
        when(registry.getAllListenerContainers()).thenReturn(List.of(container));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("stoppedListeners", List.of("user.followed-0"));
    }
}
