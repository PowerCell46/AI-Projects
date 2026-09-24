package com.peter_gerdzhikov.signal_flow_mail_service.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;

/**
 * One Kafka container for the whole suite, started once per JVM from a static initialiser before any
 * Spring context is built. Tests extend this instead of declaring their own container.
 *
 * <p>{@code apache/kafka-native:4.3.1}, not {@code 3.9.0} - the same pin the gateway's own base uses, for
 * the same {@code kafka.tools.StorageTool} advertised-listener issue against {@code 3.9.0}.
 */
public abstract class AbstractKafkaIntegrationTest {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.3.1");

    static {
        KAFKA.start();
    }

    protected static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
