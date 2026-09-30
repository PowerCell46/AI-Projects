package com.peter_gerdzhikov.twitter_tweet_service.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * One Mongo container for the whole suite: the static initialiser starts it on class load, before any
 * Spring context is built, and Testcontainers tears it down when the JVM exits. The container is a
 * single-node replica set (the 2.x class is standalone unless asked), so transactions are really exercised.
 * Tests extend this instead of declaring
 * their own container, so a run never starts more than one Mongo.
 */
@Import(TestClockConfiguration.class)
public abstract class AbstractMongoIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.2").withReplicaSet();

    static {
        MONGO.start();
    }
}
