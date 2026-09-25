package com.peter_gerdzhikov.signal_flow_mail_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Adds a Redis container next to the inherited Kafka, started once per JVM. {@code redis:8.10.2} is a
 * plain {@link GenericContainer} - there is no dedicated Testcontainers Redis module on the classpath.
 *
 * <p>Runs with {@code --requirepass}, so {@code @ServiceConnection(name = "redis")} can't be used:
 * {@code RedisContainerConnectionDetailsFactory} only ever fills in {@code Standalone.of(host, port)},
 * never a username or password, so a required password would connect unauthenticated and fail. Wired
 * manually via {@link DynamicPropertySource} instead, same as {@code AbstractMailpitIntegrationTest}.
 */
public abstract class AbstractRedisIntegrationTest extends AbstractKafkaIntegrationTest {

    protected static final String REDIS_TEST_PASSWORD = "signalflow-test-redis-password";

    private static final int REDIS_PORT = 6379;

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }
}
