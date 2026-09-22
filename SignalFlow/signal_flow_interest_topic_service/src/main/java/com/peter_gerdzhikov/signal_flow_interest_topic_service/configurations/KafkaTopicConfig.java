package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final int REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic topicNewsTopic(
            @Value("${app.kafka.topic-news.name:topic-news.generated}") String topicName,
            @Value("${app.kafka.topic-news.partitions:3}") int partitions
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
