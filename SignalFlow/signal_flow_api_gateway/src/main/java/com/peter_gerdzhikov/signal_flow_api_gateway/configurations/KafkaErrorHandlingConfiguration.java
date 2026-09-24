package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

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
     * 3 retries with backoff {@code ~1s, 2s, 4s}, then dead-letters to {@code <topic>.DLT}. Deserialization
     * failures (bad JSON) skip retries entirely and go straight to the recoverer - that's
     * {@link DeadLetterPublishingRecoverer}'s built-in handling of a {@code DeserializationException}, not
     * something configured here.
     *
     * <p>The recoverer gets its own {@code KafkaTemplate}, built here rather than exposed as a bean (a
     * second {@code KafkaTemplate} bean would make every {@code KafkaTemplate} injection elsewhere in the
     * app ambiguous). Its key serializer stays {@code String}, matching the consumer's key deserializer -
     * only the value ever fails to deserialize, so only the value needs a {@code byte[]} serializer able to
     * carry the untouched original bytes through unchanged. Bootstrap servers come from
     * {@code KafkaConnectionDetails}, not {@code kafkaProperties.buildProducerProperties()} alone - see
     * {@code KafkaConsumerConfiguration} for why a hand-built factory needs that explicitly.
     */
    @Bean
    public CommonErrorHandler topicNewsErrorHandler(KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());
        DefaultKafkaProducerFactory<String, byte[]> deadLetterProducerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, new StringSerializer(), new ByteArraySerializer());
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(new KafkaTemplate<>(deadLetterProducerFactory));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MILLIS);
        backOff.setMultiplier(MULTIPLIER);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
