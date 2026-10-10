package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.DataSeedService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs after the web server is up, because the tweet service looks the authors up at this app's internal API,
 * and after the bucket exists. The seeding itself goes on a background thread: the other services only start
 * once this one is healthy, and the seed waits for the timeline service to take its likes.
 * The bean exists only when {@code app.seed.enabled} is true. Tests set it to false: they start from an empty
 * database on purpose, and without the bean the seed password is not required. With several instances on one
 * database the first to take the advisory lock seeds and the others skip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeedRunner implements ApplicationRunner {

    private final DataSeedService dataSeedService;

    private final AdvisoryLockService advisoryLockService;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofPlatform()
                .name("data-seed")
                .start(this::seed);
    }

    private void seed() {
        try {
            Optional<Boolean> seeded = advisoryLockService.runIfUnlocked(
                    AdvisoryLockKey.DATA_SEED,
                    dataSeedService::seedIfEmpty
            );

            if (seeded.isEmpty()) {
                log.info("Skipped seeding: another instance is seeding.");

            } else if (!seeded.get()) {
                log.info("Skipped seeding: the database already has users.");
            }

        } catch (RuntimeException e) {
            log.error("Seeding the demo data failed.", e);
        }
    }
}
