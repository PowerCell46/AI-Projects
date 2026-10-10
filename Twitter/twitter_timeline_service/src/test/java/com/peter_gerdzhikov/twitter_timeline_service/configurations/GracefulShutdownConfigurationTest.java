package com.peter_gerdzhikov.twitter_timeline_service.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GracefulShutdownConfigurationTest {

    private static final Duration LONGEST_STOP_GRACE_PERIOD = Duration.ofSeconds(40);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void should_let_running_requests_finish_on_shutdown() {
        contextRunner.run(context -> assertThat(context.getEnvironment().getProperty("server.shutdown"))
                .isEqualTo("graceful"));
    }

    @Test
    void should_finish_inside_the_compose_stop_grace_period() {
        contextRunner.run(context -> {
            Duration phaseTimeout = Binder
                    .get(context.getEnvironment())
                    .bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class)
                    .get();

            assertThat(phaseTimeout).isPositive().isLessThan(LONGEST_STOP_GRACE_PERIOD);
        });
    }

    @Test
    void should_let_a_running_scheduled_job_finish_on_shutdown() {
        contextRunner.run(context -> assertThat(
                context.getEnvironment().getProperty("spring.task.scheduling.shutdown.await-termination", Boolean.class))
                .isTrue());
    }
}
