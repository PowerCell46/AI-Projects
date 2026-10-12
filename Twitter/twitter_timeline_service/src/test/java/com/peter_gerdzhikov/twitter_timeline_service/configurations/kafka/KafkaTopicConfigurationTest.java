package com.peter_gerdzhikov.twitter_timeline_service.configurations.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KafkaTopicConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(KafkaTopicConfiguration.class);

    @Test
    void should_default_all_dlts_to_a_single_replica() {
        contextRunner.run(context -> {
            NewTopic createdDlt = context.getBean("tweetCreatedDeadLetterTopic", NewTopic.class);
            NewTopic deletedDlt = context.getBean("tweetDeletedDeadLetterTopic", NewTopic.class);
            NewTopic unfollowedDlt = context.getBean("userUnfollowedDeadLetterTopic", NewTopic.class);
            NewTopic followedDlt = context.getBean("userFollowedDeadLetterTopic", NewTopic.class);

            assertThat(createdDlt.replicationFactor()).isEqualTo((short) 1);
            assertThat(deletedDlt.replicationFactor()).isEqualTo((short) 1);
            assertThat(unfollowedDlt.replicationFactor()).isEqualTo((short) 1);
            assertThat(followedDlt.replicationFactor()).isEqualTo((short) 1);
        });
    }

    @Test
    void should_apply_the_configured_replication_factor_to_all_dlts() {
        contextRunner
                .withPropertyValues("app.kafka.dlt-replication-factor=2")
                .run(context -> {
            NewTopic createdDlt = context.getBean("tweetCreatedDeadLetterTopic", NewTopic.class);
            NewTopic deletedDlt = context.getBean("tweetDeletedDeadLetterTopic", NewTopic.class);
            NewTopic unfollowedDlt = context.getBean("userUnfollowedDeadLetterTopic", NewTopic.class);
            NewTopic followedDlt = context.getBean("userFollowedDeadLetterTopic", NewTopic.class);

            assertThat(createdDlt.replicationFactor()).isEqualTo((short) 2);
            assertThat(deletedDlt.replicationFactor()).isEqualTo((short) 2);
            assertThat(unfollowedDlt.replicationFactor()).isEqualTo((short) 2);
            assertThat(followedDlt.replicationFactor()).isEqualTo((short) 2);
        });
    }
}
