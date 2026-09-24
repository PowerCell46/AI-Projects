package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.kafka;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final int MAX_RETRIES = 3;

    private static final long INITIAL_INTERVAL_MILLIS = 1000L;

    private static final double MULTIPLIER = 2.0;

    /**
     * Retries 3 times with backoff {@code ~1s, 2s, 4s}, then dead-letters to {@code <topic>.DLT}.
     * Deserialization failures skip retries entirely and go straight to the recoverer - that's
     * {@link DeadLetterPublishingRecoverer}'s own handling, not something configured here.
     */
    @Bean
    public CommonErrorHandler topicNewsErrorHandler(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        var recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate(kafkaProperties, connectionDetails));

        return new DefaultErrorHandler(recoverer, backOff());
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
    private KafkaTemplate<String, byte[]> deadLetterKafkaTemplate(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties,
                new StringSerializer(),
                new ByteArraySerializer()
        );

        return new KafkaTemplate<>(producerFactory);
    }

    private ExponentialBackOffWithMaxRetries backOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MILLIS);
        backOff.setMultiplier(MULTIPLIER);

        return backOff;
    }
}
