package com.peter_gerdzhikov.twitter_mail_service.configurations;

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

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;

/**
 * One concretely-typed consumer factory and container factory per topic: the gateway sends no type header, so
 * a properties-only {@code spring.kafka.consumer.*} config can't infer the target DTO type.
 */
@Configuration
public class KafkaConsumerConfiguration {

    @Bean
    public ConsumerFactory<String, UserConfirmationRequestedEventDTO> userConfirmationRequestedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return consumerFactory(UserConfirmationRequestedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserConfirmationRequestedEventDTO> userConfirmationRequestedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserConfirmationRequestedEventDTO> userConfirmationRequestedConsumerFactory,
            CommonErrorHandler userConfirmationRequestedErrorHandler
    ) {
        return listenerContainerFactory(concurrency, userConfirmationRequestedConsumerFactory, userConfirmationRequestedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, UserFollowedEventDTO> userFollowedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return consumerFactory(UserFollowedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserFollowedEventDTO> userFollowedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserFollowedEventDTO> userFollowedConsumerFactory,
            CommonErrorHandler userFollowedErrorHandler
    ) {
        return listenerContainerFactory(concurrency, userFollowedConsumerFactory, userFollowedErrorHandler);
    }

    /**
     * {@code buildConsumerProperties()} only carries the static bootstrap-servers value; a test's
     * {@code @ServiceConnection} container overrides it via {@link KafkaConnectionDetails} instead.
     */
    private <T> ConsumerFactory<String, T> consumerFactory(
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

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerContainerFactory(
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
