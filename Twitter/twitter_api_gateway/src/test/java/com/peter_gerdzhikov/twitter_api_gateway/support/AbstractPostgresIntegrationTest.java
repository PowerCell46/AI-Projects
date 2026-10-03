package com.peter_gerdzhikov.twitter_api_gateway.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the whole suite: the static initialiser starts it on class load, before
 * any Spring context is built, and Testcontainers tears it down when the JVM exits. Tests extend this
 * instead of declaring their own container, so a run never starts more than one Postgres. Every cached
 * Spring context keeps its own connection pool open until the JVM exits, so the connection limit is raised
 * well above the default 100, which a suite of this many context variants exhausts.
 */
@Import(TestClockConfiguration.class)
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }
}
