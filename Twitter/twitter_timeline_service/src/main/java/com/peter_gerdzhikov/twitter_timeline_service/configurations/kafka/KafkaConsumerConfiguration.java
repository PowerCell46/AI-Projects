package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

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

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
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
        return consumerFactory(TweetCreatedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TweetCreatedEventDTO> tweetCreatedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TweetCreatedEventDTO> tweetCreatedConsumerFactory,
            CommonErrorHandler tweetCreatedErrorHandler
    ) {
        return listenerContainerFactory(concurrency, tweetCreatedConsumerFactory, tweetCreatedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, TweetDeletedEventDTO> tweetDeletedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return consumerFactory(TweetDeletedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TweetDeletedEventDTO> tweetDeletedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TweetDeletedEventDTO> tweetDeletedConsumerFactory,
            CommonErrorHandler tweetDeletedErrorHandler
    ) {
        return listenerContainerFactory(concurrency, tweetDeletedConsumerFactory, tweetDeletedErrorHandler);
    }

    @Bean
    public ConsumerFactory<String, UserUnfollowedEventDTO> userUnfollowedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        return consumerFactory(UserUnfollowedEventDTO.class, kafkaProperties, connectionDetails);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserUnfollowedEventDTO> userUnfollowedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, UserUnfollowedEventDTO> userUnfollowedConsumerFactory,
            CommonErrorHandler userUnfollowedErrorHandler
    ) {
        return listenerContainerFactory(concurrency, userUnfollowedConsumerFactory, userUnfollowedErrorHandler);
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
