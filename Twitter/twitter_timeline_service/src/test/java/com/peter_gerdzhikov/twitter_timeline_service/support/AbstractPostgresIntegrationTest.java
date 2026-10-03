package com.peter_gerdzhikov.twitter_timeline_service.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the whole suite: the static initialiser starts it on class load, before
 * any Spring context is built, and Testcontainers tears it down when the JVM exits. Tests extend this
 * instead of declaring their own container, so a run never starts more than one Postgres.
 */
@Import(TestClockConfiguration.class)
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }
}
