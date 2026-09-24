package com.peter_gerdzhikov.signal_flow_mail_service.configurations;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;

@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final double MULTIPLIER = 2.0;

    /**
     * Blocking exponential backoff, then dead-letters. {@link InvalidNotificationEventException} and
     * {@link PermanentMailDeliveryException} skip retries entirely - both are permanent failures that
     * will never succeed on a later attempt. Deserialization failures skip retries on their own too -
     * that's {@link DeadLetterPublishingRecoverer}'s own handling, not configured here.
     *
     * <p>Two templates, keyed by the failed record's actual value type: a {@code byte[]} one for a
     * deserialization failure (where {@link DeadLetterPublishingRecoverer} recovers the original raw
     * bytes from an {@code ErrorHandlingDeserializer} header) and one for a {@link
     * TopicNewsNotificationEventDTO} value - every other not-retryable exception here (validation,
     * exhausted-retry held claims, permanent mail failures) fails *after* deserialization already
     * succeeded, so the recoverer holds the parsed DTO, not bytes; a single {@code byte[]}-only template
     * throws a {@code ClassCastException} trying to serialize it.
     */
    @Bean
    public CommonErrorHandler notificationRequestedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${app.kafka.retry.max-interval-ms}") long maxIntervalMs,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> deadLetterTemplatesByValueType = Map.of(
                byte[].class, deadLetterByteArrayTemplate(kafkaProperties, connectionDetails),
                TopicNewsNotificationEventDTO.class, deadLetterEventTemplate(kafkaProperties, connectionDetails));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplatesByValueType);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff(initialIntervalMs, maxIntervalMs, maxRetries));
        errorHandler.addNotRetryableExceptions(InvalidNotificationEventException.class, PermanentMailDeliveryException.class);

        return errorHandler;
    }

    /**
     * Built here rather than exposed as a bean - a second {@code KafkaTemplate} bean would make every
     * {@code KafkaTemplate} injection elsewhere in the app ambiguous. The key serializer stays
     * {@code String}, matching the consumer's key deserializer, since only the value ever fails to
     * deserialize; only it needs a {@code byte[]} serializer able to carry the untouched original bytes
     * through unchanged. Bootstrap servers come from {@link KafkaConnectionDetails}, not
     * {@code kafkaProperties.buildProducerProperties()} alone, for the same reason as the consumer-side
     * factory: a hand-built factory doesn't consult it on its own.
     */
    private KafkaTemplate<String, byte[]> deadLetterByteArrayTemplate(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, new StringSerializer(), new ByteArraySerializer());

        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaTemplate<String, TopicNewsNotificationEventDTO> deadLetterEventTemplate(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, TopicNewsNotificationEventDTO> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, new StringSerializer(), new JacksonJsonSerializer<>());

        return new KafkaTemplate<>(producerFactory);
    }

    private ExponentialBackOffWithMaxRetries backOff(long initialIntervalMs, long maxIntervalMs, int maxRetries) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMultiplier(MULTIPLIER);

        return backOff;
    }
}
