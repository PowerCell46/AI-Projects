package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.WriteConflictBudgetExceededException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;

class ConflictRetrierImplTest {

    private static final Duration BUDGET = Duration.ofSeconds(2);

    private final AtomicLong nanoTime = new AtomicLong();

    private final List<Duration> pauses = new ArrayList<>();

    private final AtomicInteger attempts = new AtomicInteger();

    private final AtomicInteger rechecks = new AtomicInteger();

    private ConflictRetrierImpl conflictRetrier;

    @BeforeEach
    void setUp() {
        conflictRetrier = new ConflictRetrierImpl(
                pause -> {
                    pauses.add(pause);
                    nanoTime.addAndGet(pause.toNanos());
                },
                nanoTime::get,
                new TransactionTemplate(mock(PlatformTransactionManager.class))
        );
    }

    @Nested
    class Execute {

        @Test
        void should_return_the_result_without_a_pause_when_the_transaction_succeeds_at_once() {
            String result = conflictRetrier.execute(() -> "done", rechecks::incrementAndGet);

            assertThat(result).isEqualTo("done");
            assertThat(pauses).isEmpty();
            assertThat(rechecks).hasValue(0);
        }

        @Test
        void should_retry_the_transaction_when_it_fails_with_a_transient_transaction_error_label() {
            MongoException conflict = new MongoException("conflict");
            conflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);

            String result = conflictRetrier.execute(failingTimes(1, conflict), rechecks::incrementAndGet);

            assertThat(result).isEqualTo("done");
            assertThat(attempts).hasValue(2);
            assertThat(rechecks).hasValue(1);
        }

        @Test
        void should_retry_the_transaction_when_it_fails_with_write_conflict_code_112() {
            String result = conflictRetrier.execute(
                    failingTimes(1, new MongoException(112, "conflict")), rechecks::incrementAndGet);

            assertThat(result).isEqualTo("done");
            assertThat(attempts).hasValue(2);
        }

        @Test
        void should_retry_the_transaction_when_the_conflict_is_the_cause_of_the_failure() {
            UncategorizedDataAccessException wrapped =
                    new UncategorizedDataAccessException("wrapped", new MongoException(112, "conflict")) {
                    };

            String result = conflictRetrier.execute(failingTimes(1, wrapped), rechecks::incrementAndGet);

            assertThat(result).isEqualTo("done");
            assertThat(attempts).hasValue(2);
        }

        @Test
        void should_pause_between_5_and_30_milliseconds_before_each_retry() {
            conflictRetrier.execute(failingTimes(5, new MongoException(112, "conflict")), rechecks::incrementAndGet);

            assertThat(pauses)
                    .hasSize(5)
                    .allSatisfy(pause -> assertThat(pause).isBetween(Duration.ofMillis(5), Duration.ofMillis(30)));
        }

        @Test
        void should_throw_busy_when_the_deadline_passes_before_the_transaction_succeeds() {
            MongoException conflict = new MongoException(112, "conflict");

            assertThatThrownBy(() -> conflictRetrier.execute(failingTimes(Integer.MAX_VALUE, conflict), rechecks::incrementAndGet))
                    .isInstanceOf(WriteConflictBudgetExceededException.class)
                    .hasCause(conflict);

            assertThat(Duration.ofNanos(nanoTime.get())).isGreaterThanOrEqualTo(BUDGET);
            assertThat(attempts.get()).isGreaterThan(1);
            assertThat(rechecks).hasValue(attempts.get());
        }

        @Test
        void should_rethrow_at_once_when_the_failure_is_not_a_write_conflict() {
            IllegalStateException failure = new IllegalStateException("mongo down");

            assertThatThrownBy(() -> conflictRetrier.execute(failingTimes(1, failure), rechecks::incrementAndGet))
                    .isSameAs(failure);

            assertThat(attempts).hasValue(1);
            assertThat(pauses).isEmpty();
            assertThat(rechecks).hasValue(0);
        }

        @Test
        void should_throw_tweet_not_found_when_the_tweet_is_gone_between_attempts() {
            Runnable tweetIsGone = () -> {
                throw new TweetNotFoundException();
            };

            assertThatThrownBy(() -> conflictRetrier.execute(failingTimes(5, new MongoException(112, "conflict")), tweetIsGone))
                    .isInstanceOf(TweetNotFoundException.class);

            assertThat(attempts).hasValue(1);
            assertThat(pauses).isEmpty();
        }

        @Test
        void should_throw_reply_not_found_when_the_reply_is_gone_between_attempts() {
            Runnable replyIsGone = () -> {
                throw new ReplyNotFoundException();
            };

            assertThatThrownBy(() -> conflictRetrier.execute(failingTimes(5, new MongoException(112, "conflict")), replyIsGone))
                    .isInstanceOf(ReplyNotFoundException.class);

            assertThat(attempts).hasValue(1);
            assertThat(pauses).isEmpty();
        }

        @Test
        void should_throw_busy_and_keep_the_interrupt_flag_when_the_pause_is_interrupted() {
            ConflictRetrierImpl interrupted = new ConflictRetrierImpl(
                    pause -> {
                        throw new InterruptedException();
                    },
                    nanoTime::get,
                    new TransactionTemplate(mock(PlatformTransactionManager.class))
            );

            try {
                assertThatThrownBy(() -> interrupted.execute(
                        failingTimes(1, new MongoException(112, "conflict")), rechecks::incrementAndGet))
                        .isInstanceOf(WriteConflictBudgetExceededException.class);

                assertThat(Thread
                        .currentThread()
                        .isInterrupted()).isTrue();

            } finally {
                Thread.interrupted();
            }
        }

        private Supplier<String> failingTimes(int times, RuntimeException failure) {
            return () -> {
                if (attempts.incrementAndGet() <= times) {
                    throw failure;
                }

                return "done";
            };
        }
    }
}
