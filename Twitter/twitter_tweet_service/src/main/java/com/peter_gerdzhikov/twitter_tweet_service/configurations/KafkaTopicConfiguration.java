package com.peter_gerdzhikov.twitter_tweet_service.configurations;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic tweetCreatedTopic(
            @Value("${app.kafka.tweet-created.name}") String name,
            @Value("${app.kafka.tweet-created.partitions}") int partitions
    ) {
        return TopicBuilder
                .name(name)
                .partitions(partitions)
                .build();
    }

    @Bean
    public NewTopic tweetDeletedTopic(
            @Value("${app.kafka.tweet-deleted.name}") String name,
            @Value("${app.kafka.tweet-deleted.partitions}") int partitions
    ) {
        return TopicBuilder
                .name(name)
                .partitions(partitions)
                .build();
    }
}
