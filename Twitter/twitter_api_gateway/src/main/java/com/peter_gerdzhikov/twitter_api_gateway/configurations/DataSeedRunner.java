package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.DataSeedService;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs after the web server is up, because the tweet service looks the authors up at this app's internal API,
 * and after the bucket exists. The seeding itself goes on a background thread: the other services only start
 * once this one is healthy, and the seed waits for the timeline service to take its likes.
 * Tests switch it off with {@code app.seed.enabled=false}: they start from an empty database on purpose.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DataSeedRunner implements ApplicationRunner {

    private final boolean enabled;

    private final DataSeedService dataSeedService;

    public DataSeedRunner(@Value("${app.seed.enabled}") boolean enabled, DataSeedService dataSeedService) {
        this.enabled = enabled;
        this.dataSeedService = dataSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Database seeding is switched off.");
            return;
        }

        Thread.ofPlatform()
                .name("data-seed")
                .start(this::seed);
    }

    private void seed() {
        try {
            if (!dataSeedService.seedIfEmpty()) {
                log.info("Skipped seeding: the database already has users.");
            }

        } catch (RuntimeException e) {
            log.error("Seeding the demo data failed.", e);
        }
    }
}
