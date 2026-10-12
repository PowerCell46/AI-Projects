package com.peter_gerdzhikov.twitter_mail_service.configurations;

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
    void should_default_both_dlts_to_a_single_replica() {
        contextRunner.run(context -> {
            NewTopic confirmationDlt = context.getBean("userConfirmationRequestedDeadLetterTopic", NewTopic.class);
            NewTopic followedDlt = context.getBean("userFollowedDeadLetterTopic", NewTopic.class);

            assertThat(confirmationDlt.replicationFactor()).isEqualTo((short) 1);
            assertThat(followedDlt.replicationFactor()).isEqualTo((short) 1);
        });
    }

    @Test
    void should_apply_the_configured_replication_factor_to_both_dlts() {
        contextRunner
                .withPropertyValues("app.kafka.dlt-replication-factor=2")
                .run(context -> {
            NewTopic confirmationDlt = context.getBean("userConfirmationRequestedDeadLetterTopic", NewTopic.class);
            NewTopic followedDlt = context.getBean("userFollowedDeadLetterTopic", NewTopic.class);

            assertThat(confirmationDlt.replicationFactor()).isEqualTo((short) 2);
            assertThat(followedDlt.replicationFactor()).isEqualTo((short) 2);
        });
    }
}
