package com.peter_gerdzhikov.signal_flow_mail_service.configurations;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractKafkaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Its own Redis container, started with a real password, and its own Spring context configured with the
 * wrong one - proves a misconfigured {@code REDIS_PASSWORD} fails loudly on first use rather than
 * connecting unauthenticated. Extends {@link AbstractKafkaIntegrationTest} directly, not
 * {@code AbstractRedisIntegrationTest}, since two {@code @DynamicPropertySource} methods registering the
 * same key would leave override order unspecified.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisAuthenticationIntegrationTest extends AbstractKafkaIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", "the-real-password");

    static {
        REDIS.start();
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "the-wrong-password");
    }

    @Test
    void should_fail_a_command_when_the_configured_password_is_wrong() {
        assertThrows(DataAccessException.class, () -> redisTemplate.opsForValue().get("any-key"));
    }
}
