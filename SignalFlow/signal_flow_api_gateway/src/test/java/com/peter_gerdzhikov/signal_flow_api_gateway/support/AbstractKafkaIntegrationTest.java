package com.peter_gerdzhikov.signal_flow_api_gateway.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Adds a Kafka container next to the inherited Postgres, started once per JVM. Tests extend this instead
 * of declaring their own container, so a run never starts more than one Kafka.
 *
 * <p>{@code apache/kafka-native:4.3.1}, not {@code 3.9.0}: against {@code 3.9.0}, {@code testcontainers-kafka}
 * 2.0.5's generated advertised-listener script made {@code kafka.tools.StorageTool} reject it as a
 * nonroutable meta-address, even though the generated value was well-formed. {@code 4.3.1} doesn't hit it.
 */
public abstract class AbstractKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.3.1");

    static {
        KAFKA.start();
    }

    /**
     * {@code @ServiceConnection} wires the container into a {@code KafkaConnectionDetails} bean, not
     * into the {@code spring.kafka.bootstrap-servers} property - so a test that needs the real address
     * for its own Kafka client reads it from here instead of via {@code @Value}, which would only see
     * the static property default.
     */
    protected static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
