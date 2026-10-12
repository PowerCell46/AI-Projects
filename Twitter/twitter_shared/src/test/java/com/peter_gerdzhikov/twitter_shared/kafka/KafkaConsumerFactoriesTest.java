package com.peter_gerdzhikov.twitter_shared.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerFactoriesTest {

    private static final String STATIC_BOOTSTRAP_SERVERS = "static:9092";

    private static final String CONTAINER_BOOTSTRAP_SERVERS = "container:9093";

    @Mock
    private CommonErrorHandler errorHandler;

    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaConnectionDetails connectionDetails;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of(STATIC_BOOTSTRAP_SERVERS));
        when(connectionDetails.getConsumer())
                .thenReturn(KafkaConnectionDetails.Configuration.of(List.of(CONTAINER_BOOTSTRAP_SERVERS)));
    }

    @Test
    void should_prefer_the_connection_details_bootstrap_servers_over_the_static_ones() {
        ConsumerFactory<String, TestEvent> factory =
                KafkaConsumerFactories.consumerFactory(TestEvent.class, kafkaProperties, connectionDetails);

        Object bootstrapServers = ((DefaultKafkaConsumerFactory<String, TestEvent>) factory)
                .getConfigurationProperties()
                .get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);

        assertThat(bootstrapServers).isEqualTo(List.of(CONTAINER_BOOTSTRAP_SERVERS));
    }

    @Test
    void should_build_a_container_factory_without_a_broker() {
        ConsumerFactory<String, TestEvent> consumerFactory =
                KafkaConsumerFactories.consumerFactory(TestEvent.class, kafkaProperties, connectionDetails);

        ConcurrentKafkaListenerContainerFactory<String, TestEvent> containerFactory =
                KafkaConsumerFactories.listenerContainerFactory(3, consumerFactory, errorHandler);

        assertThat(containerFactory).isNotNull();
    }

    private static class TestEvent {

        @SuppressWarnings("unused")
        private String name;
    }
}
