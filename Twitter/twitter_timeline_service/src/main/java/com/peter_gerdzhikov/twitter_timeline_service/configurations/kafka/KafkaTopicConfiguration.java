package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares only the dead-letter topics - the main topics belong to the tweet service and the gateway, and aren't
 * redeclared here.
 */
@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic tweetCreatedDeadLetterTopic(
            @Value("${app.kafka.tweet-created.dlt-name}") String dltName,
            @Value("${app.kafka.tweet-created.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return deadLetterTopic(dltName, partitions, replicationFactor);
    }

    @Bean
    public NewTopic tweetDeletedDeadLetterTopic(
            @Value("${app.kafka.tweet-deleted.dlt-name}") String dltName,
            @Value("${app.kafka.tweet-deleted.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return deadLetterTopic(dltName, partitions, replicationFactor);
    }

    @Bean
    public NewTopic userUnfollowedDeadLetterTopic(
            @Value("${app.kafka.user-unfollowed.dlt-name}") String dltName,
            @Value("${app.kafka.user-unfollowed.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return deadLetterTopic(dltName, partitions, replicationFactor);
    }

    @Bean
    public NewTopic userFollowedDeadLetterTopic(
            @Value("${app.kafka.user-followed.dlt-name}") String dltName,
            @Value("${app.kafka.user-followed.dlt-partitions}") int partitions,
            @Value("${app.kafka.dlt-replication-factor}") int replicationFactor
    ) {
        return deadLetterTopic(dltName, partitions, replicationFactor);
    }

    private NewTopic deadLetterTopic(String name, int partitions, int replicationFactor) {
        return TopicBuilder
                .name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
