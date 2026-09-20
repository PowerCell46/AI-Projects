package com.peter_gerdzhikov.url_shortener_backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import com.redis.testcontainers.RedisContainer;

/**
 * One Redis container for the whole suite: the static initialiser starts it on class load, before
 * any Spring context is built, and Testcontainers tears it down when the JVM exits. Tests extend
 * this instead of declaring their own container, so a run never starts more than one Redis.
 */
public abstract class AbstractRedisIntegrationTest {

    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer("redis:8.2");

    static {
        REDIS.start();
    }
}
