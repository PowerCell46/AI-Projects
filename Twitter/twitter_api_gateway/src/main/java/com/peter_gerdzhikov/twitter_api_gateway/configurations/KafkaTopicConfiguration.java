package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic userConfirmationRequestedTopic(
            @Value("${app.kafka.user-confirmation-requested.name}") String name,
            @Value("${app.kafka.user-confirmation-requested.partitions}") int partitions
    ) {
        return TopicBuilder
                .name(name)
                .partitions(partitions)
                .build();
    }

    @Bean
    public NewTopic userFollowedTopic(
            @Value("${app.kafka.user-followed.name}") String name,
            @Value("${app.kafka.user-followed.partitions}") int partitions
    ) {
        return TopicBuilder
                .name(name)
                .partitions(partitions)
                .build();
    }
}
