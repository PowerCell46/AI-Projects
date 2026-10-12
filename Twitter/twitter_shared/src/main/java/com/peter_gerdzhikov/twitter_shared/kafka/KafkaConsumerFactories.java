package com.peter_gerdzhikov.twitter_shared.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/**
 * Shared consumer/container factory helpers: the gateway sends no type header, so a properties-only
 * {@code spring.kafka.consumer.*} config can't infer the target DTO type and each service still declares
 * one concretely-typed factory pair per topic on top of these.
 */
public final class KafkaConsumerFactories {

    private KafkaConsumerFactories() {
    }

    /**
     * {@code buildConsumerProperties()} only carries the static bootstrap-servers value; a test's
     * {@code @ServiceConnection} container overrides it via {@link KafkaConnectionDetails} instead.
     */
    public static <T> ConsumerFactory<String, T> consumerFactory(
            Class<T> valueType,
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumer().getBootstrapServers());

        return new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JacksonJsonDeserializer<>(valueType).ignoreTypeHeaders())
        );
    }

    public static <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
            int concurrency,
            ConsumerFactory<String, T> consumerFactory,
            CommonErrorHandler errorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(concurrency);

        return factory;
    }
}
