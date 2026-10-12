package com.peter_gerdzhikov.twitter_mail_service.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_shared.kafka.KafkaConsumerFactories;

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
        return KafkaConsumerFactories.consumerFactory(UserConfirmationRequestedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserConfirmationRequestedEventDTO> userConfirmationRequestedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserConfirmationRequestedEventDTO> userConfirmationRequestedConsumerFactory,
            CommonErrorHandler userConfirmationRequestedErrorHandler
    ) {
        return KafkaConsumerFactories.listenerContainerFactory(concurrency, userConfirmationRequestedConsumerFactory, userConfirmationRequestedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, UserFollowedEventDTO> userFollowedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return KafkaConsumerFactories.consumerFactory(UserFollowedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserFollowedEventDTO> userFollowedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserFollowedEventDTO> userFollowedConsumerFactory,
            CommonErrorHandler userFollowedErrorHandler
    ) {
        return KafkaConsumerFactories.listenerContainerFactory(concurrency, userFollowedConsumerFactory, userFollowedErrorHandler);
    }
}
