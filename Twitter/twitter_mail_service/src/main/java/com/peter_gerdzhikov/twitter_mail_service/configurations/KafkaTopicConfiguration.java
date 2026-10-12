package com.peter_gerdzhikov.twitter_mail_service.configurations;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares only the dead-letter topics - the main topics are the gateway's own and aren't redeclared here.
 */
@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic userConfirmationRequestedDeadLetterTopic(
            @Value("${app.kafka.user-confirmation-requested.dlt-name}") String dltName,
            @Value("${app.kafka.user-confirmation-requested.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return TopicBuilder
                .name(dltName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic userFollowedDeadLetterTopic(
            @Value("${app.kafka.user-followed.dlt-name}") String dltName,
            @Value("${app.kafka.user-followed.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return TopicBuilder
                .name(dltName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
