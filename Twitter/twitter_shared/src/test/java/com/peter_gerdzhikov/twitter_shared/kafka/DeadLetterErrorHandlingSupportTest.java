package com.peter_gerdzhikov.twitter_shared.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@ExtendWith(MockitoExtension.class)
class DeadLetterErrorHandlingSupportTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private KafkaProperties kafkaProperties;

    private TestErrorHandling errorHandling;

    @Mock
    private KafkaConnectionDetails connectionDetails;

    @BeforeEach
    void setUp() {
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of(BOOTSTRAP_SERVERS));
        errorHandling = new TestErrorHandling();
    }

    @Test
    void should_build_the_backoff_from_the_given_intervals_and_retries() {
        ExponentialBackOffWithMaxRetries backOff =
                errorHandling.exposedBackOff(Duration.ofMillis(100), Duration.ofMillis(800), 3);

        assertThat(backOff.getInitialInterval()).isEqualTo(100L);
        assertThat(backOff.getMaxInterval()).isEqualTo(800L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
        assertThat(backOff.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void should_build_a_default_error_handler_without_a_broker() {
        givenProducerConnectionDetails();

        CommonErrorHandler handler = errorHandling.exposedErrorHandler(
                TestEvent.class,
                "some.dlt",
                kafkaProperties,
                connectionDetails,
                errorHandling.exposedBackOff(Duration.ofMillis(100), Duration.ofMillis(800), 3),
                IllegalArgumentException.class
        );

        assertThat(handler).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void should_close_the_producers_its_templates_built_on_destroy() {
        givenProducerConnectionDetails();

        KafkaTemplate<String, String> template =
                errorHandling.exposedTemplate(kafkaProperties, connectionDetails, new StringSerializer());
        Producer<String, String> producer = template.getProducerFactory().createProducer();

        errorHandling.destroy();

        assertThatThrownBy(() -> producer.send(new ProducerRecord<>("some.dlt", "key", "value")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_survive_destroy_without_any_templates_and_more_than_once() {
        givenProducerConnectionDetails();

        errorHandling.exposedErrorHandler(
                TestEvent.class,
                "some.dlt",
                kafkaProperties,
                connectionDetails,
                errorHandling.exposedBackOff(Duration.ofMillis(100), Duration.ofMillis(800), 3),
                IllegalArgumentException.class
        );

        assertThatCode(() -> {
            errorHandling.destroy();
            errorHandling.destroy();
        }).doesNotThrowAnyException();
    }

    private void givenProducerConnectionDetails() {
        when(connectionDetails.getProducer())
                .thenReturn(KafkaConnectionDetails.Configuration.of(List.of(BOOTSTRAP_SERVERS)));
    }

    private static class TestErrorHandling extends DeadLetterErrorHandlingSupport {

        private ExponentialBackOffWithMaxRetries exposedBackOff(Duration initialInterval, Duration maxInterval, int maxRetries) {
            return backOff(initialInterval, maxInterval, maxRetries);
        }

        private <T> CommonErrorHandler exposedErrorHandler(
                Class<T> eventType,
                String dltName,
                KafkaProperties kafkaProperties,
                KafkaConnectionDetails connectionDetails,
                ExponentialBackOffWithMaxRetries backOff,
                Class<? extends Exception>... notRetryableExceptions
        ) {
            return errorHandler(eventType, dltName, kafkaProperties, connectionDetails, backOff, notRetryableExceptions);
        }

        private <V> KafkaTemplate<String, V> exposedTemplate(
                KafkaProperties kafkaProperties,
                KafkaConnectionDetails connectionDetails,
                Serializer<V> valueSerializer
        ) {
            return deadLetterTemplate(kafkaProperties, connectionDetails, valueSerializer);
        }
    }

    private static class TestEvent {

        @SuppressWarnings("unused")
        private String name;
    }
}
