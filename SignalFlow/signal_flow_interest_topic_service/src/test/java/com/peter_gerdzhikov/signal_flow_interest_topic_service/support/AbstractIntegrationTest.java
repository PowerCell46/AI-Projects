package com.peter_gerdzhikov.signal_flow_interest_topic_service.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Postgres and one Kafka container for the whole suite: the static initialiser starts both on
 * class load, before any Spring context is built, and Testcontainers tears them down when the JVM
 * exits. Tests extend this instead of declaring their own containers, so a run never starts more than
 * one of each.
 *
 * <p>Both {@code @Scheduled} jobs are disabled by default: every {@code @SpringBootTest} shares this
 * same Postgres/Kafka pair, and Spring caches contexts across test classes for the whole suite run, so
 * a live scheduler from one test's context would otherwise keep firing against the shared database
 * while a different test is running, racing whichever test's rows happen to be due at that moment. A
 * test that means to exercise a scheduler re-enables just its own with a local
 * {@code @TestPropertySource} override, which takes precedence over this class's.
 */
@TestPropertySource(properties = {"app.news.cron=-", "app.outbox.poll-interval=PT24H"})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.3.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    /**
     * {@code @ServiceConnection} wires the container straight into a {@code KafkaConnectionDetails}
     * bean, not into the {@code spring.kafka.bootstrap-servers} property - so a test that needs the
     * real address for its own Kafka client (e.g. a consumer verifying a published message) reads it
     * from the container directly instead of via {@code @Value}, which would only see the static
     * property default.
     */
    protected static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
