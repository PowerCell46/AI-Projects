package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.WriteConflictBudgetExceededException;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.ConflictRetrier;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.retry.MonotonicTimeSource;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.retry.Sleeper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictRetrierImpl implements ConflictRetrier {

    private static final int MIN_PAUSE_MILLIS = 5;

    private static final int MAX_PAUSE_MILLIS = 30;

    private static final int WRITE_CONFLICT_CODE = 112;

    private static final Duration RETRY_BUDGET = Duration.ofSeconds(2);

    private final Sleeper sleeper;

    private final MonotonicTimeSource timeSource;

    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T execute(Supplier<T> transaction, Runnable recheckAfterConflict) {
        long deadlineNanos = timeSource.nanoTime() + RETRY_BUDGET.toNanos();

        while (true) {
            try {
                return transactionTemplate.execute(status -> transaction.get());

            } catch (RuntimeException e) {
                if (!isWriteConflict(e)) {
                    throw e;
                }

                recheckAfterConflict.run();
                if (timeSource.nanoTime() - deadlineNanos >= 0) {
                    log.warn("A transaction kept conflicting with concurrent writers; giving up.");
                    throw new WriteConflictBudgetExceededException(e);
                }

                pauseBeforeRetry(e);
            }
        }
    }

    private void pauseBeforeRetry(RuntimeException conflict) {
        long pauseMillis = ThreadLocalRandom
                .current()
                .nextLong(MIN_PAUSE_MILLIS, MAX_PAUSE_MILLIS + 1);

        try {
            sleeper.sleep(Duration.ofMillis(pauseMillis));

        } catch (InterruptedException e) {
            Thread
                    .currentThread()
                    .interrupt();
            throw new WriteConflictBudgetExceededException(conflict);
        }
    }

    private boolean isWriteConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (
                    cause instanceof MongoException mongoException
                            && (mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                            || mongoException.getCode() == WRITE_CONFLICT_CODE)
            ) {
                return true;
            }
        }

        return false;
    }
}
