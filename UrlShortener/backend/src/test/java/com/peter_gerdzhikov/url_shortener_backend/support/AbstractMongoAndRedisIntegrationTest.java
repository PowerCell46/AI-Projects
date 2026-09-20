package com.peter_gerdzhikov.url_shortener_backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;

import com.redis.testcontainers.RedisContainer;

/**
 * One Mongo and one Redis container for the whole suite, for tests that exercise both together
 * (e.g. the redirect flow, which warms the cache from Mongo). See
 * {@link AbstractMongoIntegrationTest} / {@link AbstractRedisIntegrationTest} for the single-store
 * equivalents and why the containers are started from a static initialiser rather than
 * {@code @Testcontainers}/{@code @Container}.
 */
public abstract class AbstractMongoAndRedisIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8.2");

    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer("redis:8.2");

    static {
        MONGO_DB.start();
        REDIS.start();
    }
}
