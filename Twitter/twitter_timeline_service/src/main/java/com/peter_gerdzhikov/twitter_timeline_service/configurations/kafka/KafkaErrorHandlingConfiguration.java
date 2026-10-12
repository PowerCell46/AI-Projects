package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;

import com.peter_gerdzhikov.twitter_shared.kafka.DeadLetterErrorHandlingSupport;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;

@Configuration
public class KafkaErrorHandlingConfiguration extends DeadLetterErrorHandlingSupport {

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
                backOff(initialInterval, maxInterval, maxRetries),
                InvalidEventException.class);
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
                backOff(initialInterval, maxInterval, maxRetries),
                InvalidEventException.class);
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
                backOff(initialInterval, maxInterval, maxRetries),
                InvalidEventException.class);
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
                backOff(initialInterval, maxInterval, maxRetries),
                InvalidEventException.class);
    }
}
