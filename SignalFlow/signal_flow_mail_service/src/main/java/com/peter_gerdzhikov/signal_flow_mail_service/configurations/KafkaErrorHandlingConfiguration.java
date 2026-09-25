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
     * {@link PermanentMailDeliveryException} skip retries as permanent failures; deserialization
     * failures skip retries on their own via {@link DeadLetterPublishingRecoverer}. Two dead-letter
     * templates are keyed by value type because a deserialization failure recovers raw {@code byte[]},
     * while every other failure here happens after deserialization already succeeded and recovers the
     * parsed DTO instead.
     */
    @Bean
    public CommonErrorHandler topicNewsNotificationRequestedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${app.kafka.retry.max-interval-ms}") long maxIntervalMs,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> deadLetterTemplatesByValueType = Map.of(
                byte[].class, deadLetterByteArrayTemplate(kafkaProperties, connectionDetails),
                TopicNewsNotificationEventDTO.class, deadLetterEventTemplate(kafkaProperties, connectionDetails)
        );

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplatesByValueType);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff(initialIntervalMs, maxIntervalMs, maxRetries));
        errorHandler.addNotRetryableExceptions(InvalidNotificationEventException.class, PermanentMailDeliveryException.class);

        return errorHandler;
    }

    /**
     * Built here, not exposed as a bean - a second {@code KafkaTemplate} bean would make every
     * {@code KafkaTemplate} injection elsewhere ambiguous.
     */
    private KafkaTemplate<String, byte[]> deadLetterByteArrayTemplate(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, new StringSerializer(), new ByteArraySerializer()
        );

        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaTemplate<String, TopicNewsNotificationEventDTO> deadLetterEventTemplate(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, TopicNewsNotificationEventDTO> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, new StringSerializer(), new JacksonJsonSerializer<>()
        );

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
