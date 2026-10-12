package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectRetryTest {

    private static final Duration FAST_RETRY_DELAY = Duration.ofMillis(10);

    @Nested
    class SupplyWithConnectRetry {

        @Test
        void should_return_the_first_answer_without_waiting() {
            String answer = ConnectRetry.supplyWithConnectRetry(() -> "ok", "the test service", 3, FAST_RETRY_DELAY);

            assertThat(answer).isEqualTo("ok");
        }

        @Test
        void should_retry_a_refused_connection_until_the_call_answers() {
            AtomicInteger calls = new AtomicInteger();

            String answer = ConnectRetry.supplyWithConnectRetry(() -> {
                if (calls.incrementAndGet() < 3) {
                    throw new ResourceAccessException("refused");
                }

                return "ok";
            }, "the test service", 3, FAST_RETRY_DELAY);

            assertThat(answer).isEqualTo("ok");
            assertThat(calls.get()).isEqualTo(3);
        }

        @Test
        void should_rethrow_after_the_last_attempt() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> ConnectRetry.supplyWithConnectRetry(() -> {
                calls.incrementAndGet();
                throw new ResourceAccessException("refused");
            }, "the test service", 3, FAST_RETRY_DELAY))
                    .isInstanceOf(ResourceAccessException.class);

            assertThat(calls.get()).isEqualTo(3);
        }

        @Test
        void should_restore_the_interrupt_flag_and_fail_when_interrupted_while_waiting() throws Exception {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interruptFlag = new AtomicBoolean();
            Thread worker = Thread.ofPlatform().start(() -> {
                try {
                    ConnectRetry.supplyWithConnectRetry(() -> {
                        throw new ResourceAccessException("refused");
                    }, "the test service", 36, Duration.ofMinutes(1));

                } catch (IllegalStateException ex) {
                    failure.set(ex);
                    interruptFlag.set(Thread.currentThread().isInterrupted());
                }
            });

            Thread.sleep(200);
            worker.interrupt();
            worker.join(5_000);

            assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
            assertThat(failure.get()).hasMessage("Interrupted while waiting for the test service.");
            assertThat(interruptFlag.get()).isTrue();
            assertThat(worker.isAlive()).isFalse();
        }
    }

    @Nested
    class RunWithConnectRetry {

        @Test
        void should_run_the_call_once_when_it_answers() {
            AtomicInteger calls = new AtomicInteger();

            ConnectRetry.runWithConnectRetry(calls::incrementAndGet, "the test service", 3, FAST_RETRY_DELAY);

            assertThat(calls.get()).isEqualTo(1);
        }

        @Test
        void should_not_retry_an_answer_from_the_service() {
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> ConnectRetry.runWithConnectRetry(() -> {
                calls.incrementAndGet();
                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
            }, "the test service", 3, FAST_RETRY_DELAY))
                    .isInstanceOf(HttpClientErrorException.class);

            assertThat(calls.get()).isEqualTo(1);
        }
    }
}
