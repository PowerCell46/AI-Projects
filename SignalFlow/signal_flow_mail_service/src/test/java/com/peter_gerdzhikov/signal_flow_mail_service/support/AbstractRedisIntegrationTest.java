package com.peter_gerdzhikov.signal_flow_mail_service.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Adds a Redis container next to the inherited Kafka, started once per JVM. {@code redis:8.10.2} is a
 * plain {@link GenericContainer} - there is no dedicated Testcontainers Redis module on the classpath -
 * so the connection name is given explicitly rather than relying on image-name auto-detection.
 */
public abstract class AbstractRedisIntegrationTest extends AbstractKafkaIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
            .withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }
}
