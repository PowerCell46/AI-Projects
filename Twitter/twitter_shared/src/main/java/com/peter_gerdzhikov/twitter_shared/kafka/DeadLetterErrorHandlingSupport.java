package com.peter_gerdzhikov.twitter_shared.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * The dead-letter half of each consumer. The templates stay out of the context - a second
 * {@code KafkaTemplate} bean would make every {@code KafkaTemplate} injection elsewhere ambiguous - so
 * this support tracks their factories and closes them on shutdown instead.
 */
public abstract class DeadLetterErrorHandlingSupport implements DisposableBean {

    private static final double MULTIPLIER = 2.0;

    /**
     * An unset partition lets the producer partition by the record key, so one user's dead letters stay together.
     */
    private static final int UNSET_PARTITION = -1;

    private final List<DefaultKafkaProducerFactory<?, ?>> producerFactories = new ArrayList<>();

    @Override
    public void destroy() {
        producerFactories.forEach(DefaultKafkaProducerFactory::destroy);
    }

    /**
     * Blocking exponential backoff, then dead-letters to {@code dltName}. The
     * {@code notRetryableExceptions} skip the retries as permanent failures; deserialization failures
     * skip them on their own. Two dead-letter templates are keyed by value type because a deserialization
     * failure recovers the raw {@code byte[]}, while every other failure here happens after deserialization
     * already succeeded and recovers the parsed event instead.
     */
    protected <T> CommonErrorHandler errorHandler(
            Class<T> eventType,
            String dltName,
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            ExponentialBackOffWithMaxRetries backOff,
            Class<? extends Exception>... notRetryableExceptions
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> deadLetterTemplatesByValueType = Map.of(
                byte[].class, deadLetterTemplate(kafkaProperties, connectionDetails, new ByteArraySerializer()),
                eventType, deadLetterTemplate(kafkaProperties, connectionDetails, new JacksonJsonSerializer<T>())
        );

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplatesByValueType,
                (record, exception) -> new TopicPartition(dltName, UNSET_PARTITION));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(notRetryableExceptions);

        return errorHandler;
    }

    protected ExponentialBackOffWithMaxRetries backOff(Duration initialInterval, Duration maxInterval, int maxRetries) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMaxInterval(maxInterval.toMillis());
        backOff.setMultiplier(MULTIPLIER);

        return backOff;
    }

    /**
     * Builds one dead-letter template and tracks its factory, so {@link #destroy()} closes it on shutdown.
     */
    protected <V> KafkaTemplate<String, V> deadLetterTemplate(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            Serializer<V> valueSerializer
    ) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, V> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties,
                new StringSerializer(),
                valueSerializer
        );
        producerFactories.add(producerFactory);

        return new KafkaTemplate<>(producerFactory);
    }
}
