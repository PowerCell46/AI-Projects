package com.peter_gerdzhikov.twitter_mail_service.configurations;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.twitter_shared.kafka.DeadLetterErrorHandlingSupport;

@Configuration
public class KafkaErrorHandlingConfiguration extends DeadLetterErrorHandlingSupport {

    @Bean
    public CommonErrorHandler userConfirmationRequestedErrorHandler(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.user-confirmation-requested.dlt-name}") String dltName,
            @Value("${app.kafka.retry.initial-interval}") Duration initialInterval,
            @Value("${app.kafka.retry.max-interval}") Duration maxInterval,
            @Value("${app.kafka.retry.max-retries}") int maxRetries
    ) {
        return errorHandler(
                UserConfirmationRequestedEventDTO.class,
                dltName,
                kafkaProperties,
                connectionDetails,
                backOff(initialInterval, maxInterval, maxRetries),
                InvalidMailEventException.class,
                PermanentMailDeliveryException.class);
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
                InvalidMailEventException.class,
                PermanentMailDeliveryException.class);
    }
}
