package com.peter_gerdzhikov.url_shortener_backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * One Mongo container for the whole suite: the static initialiser starts it on class load, before
 * any Spring context is built, and Testcontainers tears it down when the JVM exits. Tests extend
 * this instead of declaring their own container, so a run never starts more than one Mongo.
 */
public abstract class AbstractMongoIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:8.2");

    static {
        MONGO_DB.start();
    }
}
