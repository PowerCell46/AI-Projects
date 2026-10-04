package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
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

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;

@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final double MULTIPLIER = 2.0;

    /**
     * An unset partition lets the producer partition by the record key, so one tweet's (or one user's) dead
     * letters stay together.
     */
    private static final int UNSET_PARTITION = -1;

    @Bean
    public CommonErrorHandler tweetCreatedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.tweet-created.dlt-name}") String dltName,
            @Value("${app.kafka.retry.initial-interval}") Duration initialInterval,
            @Value("${app.kafka.retry.max-interval}") Duration maxInterval,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        return errorHandler(
                TweetCreatedEventDTO.class,
                dltName,
                kafkaProperties,
                connectionDetails,
                backOff(initialInterval, maxInterval, maxRetries));
    }

    @Bean
    public CommonErrorHandler tweetDeletedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.tweet-deleted.dlt-name}") String dltName,
            @Value("${app.kafka.retry.initial-interval}") Duration initialInterval,
            @Value("${app.kafka.retry.max-interval}") Duration maxInterval,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        return errorHandler(
                TweetDeletedEventDTO.class,
                dltName,
                kafkaProperties,
                connectionDetails,
                backOff(initialInterval, maxInterval, maxRetries));
    }

    @Bean
    public CommonErrorHandler userUnfollowedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.user-unfollowed.dlt-name}") String dltName,
            @Value("${app.kafka.retry.initial-interval}") Duration initialInterval,
            @Value("${app.kafka.retry.max-interval}") Duration maxInterval,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        return errorHandler(
                UserUnfollowedEventDTO.class,
                dltName,
                kafkaProperties,
                connectionDetails,
                backOff(initialInterval, maxInterval, maxRetries));
    }

    @Bean
    public CommonErrorHandler userFollowedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.user-followed.dlt-name}") String dltName,
            @Value("${app.kafka.retry.initial-interval}") Duration initialInterval,
            @Value("${app.kafka.retry.max-interval}") Duration maxInterval,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        return errorHandler(
                UserFollowedEventDTO.class,
                dltName,
                kafkaProperties,
                connectionDetails,
                backOff(initialInterval, maxInterval, maxRetries));
    }

    /**
     * Blocking exponential backoff, then dead-letters to {@code dltName}. {@link InvalidEventException} skips
     * the retries as a permanent failure; deserialization failures skip them on their own. Two dead-letter
     * templates are keyed by value type because a deserialization failure recovers the raw {@code byte[]},
     * while every other failure here happens after deserialization already succeeded and recovers the parsed
     * event instead.
     */
    private <T> CommonErrorHandler errorHandler(
            Class<T> eventType,
            String dltName,
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            ExponentialBackOffWithMaxRetries backOff
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> deadLetterTemplatesByValueType = Map.of(
                byte[].class, deadLetterTemplate(kafkaProperties, connectionDetails, new ByteArraySerializer()),
                eventType, deadLetterTemplate(kafkaProperties, connectionDetails, new JacksonJsonSerializer<T>())
        );

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplatesByValueType,
                (record, exception) -> new TopicPartition(dltName, UNSET_PARTITION));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);

        return errorHandler;
    }

    /**
     * Built here, not exposed as a bean - a second {@code KafkaTemplate} bean would make every
     * {@code KafkaTemplate} injection elsewhere ambiguous.
     */
    private <V> KafkaTemplate<String, V> deadLetterTemplate(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            Serializer<V> valueSerializer
    ) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), valueSerializer));
    }

    private ExponentialBackOffWithMaxRetries backOff(Duration initialInterval, Duration maxInterval, int maxRetries) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMaxInterval(maxInterval.toMillis());
        backOff.setMultiplier(MULTIPLIER);

        return backOff;
    }
}
