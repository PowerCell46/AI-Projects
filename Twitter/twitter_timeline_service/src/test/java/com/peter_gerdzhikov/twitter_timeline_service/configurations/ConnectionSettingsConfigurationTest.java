package com.peter_gerdzhikov.twitter_timeline_service.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class ConnectionSettingsConfigurationTest {

    private static final int LISTENER_THREADS = 12;

    private static final int GATEWAY_READ_TIMEOUT_MILLIS = 10_000;

    private static final int MAX_POLL_RECORDS_CEILING = 100;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void should_give_every_listener_thread_a_connection_with_room_left_for_requests() {
        contextRunner.run(context -> assertThat(property(context.getEnvironment(), "spring.datasource.hikari.maximum-pool-size"))
                .isGreaterThan(LISTENER_THREADS));
    }

    @Test
    void should_stop_waiting_for_a_connection_before_the_gateway_gives_up() {
        contextRunner.run(context -> assertThat(property(context.getEnvironment(), "spring.datasource.hikari.connection-timeout"))
                .isLessThan(GATEWAY_READ_TIMEOUT_MILLIS));
    }

    @Test
    void should_poll_small_batches_because_every_record_makes_an_http_call() {
        contextRunner.run(context -> assertThat(property(context.getEnvironment(), "spring.kafka.consumer.max-poll-records"))
                .isBetween(1, MAX_POLL_RECORDS_CEILING));
    }

    private int property(Environment environment, String name) {
        return Binder
                .get(environment)
                .bind(name, Integer.class)
                .get();
    }
}
