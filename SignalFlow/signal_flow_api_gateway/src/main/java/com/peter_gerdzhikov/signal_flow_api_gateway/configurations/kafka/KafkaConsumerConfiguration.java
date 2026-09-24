package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.kafka;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;

/**
 * A dedicated, concretely-typed listener container factory for {@code topic-news.generated} - needed
 * because the value deserializer (an {@code ErrorHandlingDeserializer} wrapping a
 * {@code JacksonJsonDeserializer} bound to {@link TopicNewsEventDTO}) has to be a real object: type headers
 * are off, so there is no header for a properties-only {@code spring.kafka.consumer.*} configuration to
 * infer the target type from.
 */
@Configuration
public class KafkaConsumerConfiguration {

    /**
     * {@code kafkaProperties.buildConsumerProperties()} only ever returns the static
     * {@code spring.kafka.bootstrap-servers} value - a test's {@code @ServiceConnection} container overrides
     * the address through this {@code KafkaConnectionDetails} bean instead, which Boot's own auto-configured
     * factories consult but a hand-built one like this must consult explicitly.
     */
    @Bean
    public ConsumerFactory<String, TopicNewsEventDTO> topicNewsConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumer().getBootstrapServers());

        return new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JacksonJsonDeserializer<>(TopicNewsEventDTO.class).ignoreTypeHeaders())
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TopicNewsEventDTO> topicNewsListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TopicNewsEventDTO> topicNewsConsumerFactory,
            CommonErrorHandler topicNewsErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, TopicNewsEventDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(topicNewsConsumerFactory);
        factory.setCommonErrorHandler(topicNewsErrorHandler);
        factory.setConcurrency(concurrency);
        return factory;
    }
}
