package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.DataSeedService;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DataSeedRunnerTest {

    private static final Duration SEED_TIMEOUT = Duration.ofSeconds(5);

    @Mock
    private DataSeedService dataSeedService;

    @Mock
    private AdvisoryLockService advisoryLockService;

    private DataSeedRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DataSeedRunner(dataSeedService, advisoryLockService);
    }

    @Test
    void should_seed_when_the_lock_is_free_and_the_database_is_empty() {
        whenLockRunsTheAction();
        when(dataSeedService.seedIfEmpty()).thenReturn(true);

        runner.run(null);

        await().atMost(SEED_TIMEOUT).untilAsserted(() -> verify(dataSeedService).seedIfEmpty());
    }

    @Test
    void should_skip_seeding_when_another_instance_holds_the_lock(CapturedOutput output) {
        when(advisoryLockService.runIfUnlocked(eq(AdvisoryLockKey.DATA_SEED), any())).thenReturn(Optional.empty());

        runner.run(null);

        await().atMost(SEED_TIMEOUT).untilAsserted(
                () -> verify(advisoryLockService).runIfUnlocked(eq(AdvisoryLockKey.DATA_SEED), any()));
        verify(dataSeedService, never()).seedIfEmpty();
        assertThat(output.getOut()).contains("another instance is seeding");
    }

    @Test
    void should_skip_seeding_when_the_database_already_has_users(CapturedOutput output) {
        whenLockRunsTheAction();
        when(dataSeedService.seedIfEmpty()).thenReturn(false);

        runner.run(null);

        await().atMost(SEED_TIMEOUT).untilAsserted(() -> verify(dataSeedService).seedIfEmpty());
        assertThat(output.getOut()).contains("already has users");
    }

    @Test
    void should_log_and_swallow_a_seeding_failure(CapturedOutput output) {
        whenLockRunsTheAction();
        when(dataSeedService.seedIfEmpty()).thenThrow(new IllegalStateException("db down"));

        runner.run(null);

        await().atMost(SEED_TIMEOUT).until(() -> output.getOut().contains("Seeding the demo data failed."));
    }

    private void whenLockRunsTheAction() {
        when(advisoryLockService.<Boolean>runIfUnlocked(eq(AdvisoryLockKey.DATA_SEED), any()))
                .thenAnswer(invocation -> {
                    Supplier<Boolean> action = invocation.getArgument(1);

                    return Optional.of(action.get());
                });
    }
}
