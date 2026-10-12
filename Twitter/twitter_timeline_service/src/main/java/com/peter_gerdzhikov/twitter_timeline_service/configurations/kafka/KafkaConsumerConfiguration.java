package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

import com.peter_gerdzhikov.twitter_shared.kafka.KafkaConsumerFactories;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;

/**
 * One concretely-typed consumer factory and container factory per topic: the producers send no type header, so
 * a properties-only {@code spring.kafka.consumer.*} config can't infer the target DTO type.
 */
@Configuration
public class KafkaConsumerConfiguration {

    @Bean
    public ConsumerFactory<String, TweetCreatedEventDTO> tweetCreatedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return KafkaConsumerFactories.consumerFactory(TweetCreatedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TweetCreatedEventDTO> tweetCreatedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TweetCreatedEventDTO> tweetCreatedConsumerFactory,
            CommonErrorHandler tweetCreatedErrorHandler
    ) {
        return KafkaConsumerFactories.listenerContainerFactory(concurrency, tweetCreatedConsumerFactory, tweetCreatedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, TweetDeletedEventDTO> tweetDeletedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return KafkaConsumerFactories.consumerFactory(TweetDeletedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TweetDeletedEventDTO> tweetDeletedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TweetDeletedEventDTO> tweetDeletedConsumerFactory,
            CommonErrorHandler tweetDeletedErrorHandler
    ) {
        return KafkaConsumerFactories.listenerContainerFactory(concurrency, tweetDeletedConsumerFactory, tweetDeletedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, UserUnfollowedEventDTO> userUnfollowedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return KafkaConsumerFactories.consumerFactory(UserUnfollowedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserUnfollowedEventDTO> userUnfollowedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserUnfollowedEventDTO> userUnfollowedConsumerFactory,
            CommonErrorHandler userUnfollowedErrorHandler
    ) {
        return KafkaConsumerFactories.listenerContainerFactory(concurrency, userUnfollowedConsumerFactory, userUnfollowedErrorHandler);
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
