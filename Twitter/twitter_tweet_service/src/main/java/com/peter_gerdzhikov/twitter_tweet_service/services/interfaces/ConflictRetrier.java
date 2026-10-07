package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

import java.util.function.Supplier;

import com.peter_gerdzhikov.twitter_tweet_service.exceptions.WriteConflictBudgetExceededException;

public interface ConflictRetrier {

    /**
     * Runs the transaction, and runs it again after a short random pause whenever it fails with a write
     * conflict, until about two seconds have passed since the first attempt. Any other failure is rethrown at
     * once.
     *
     * @param recheckAfterConflict runs after every conflict, before the next attempt; it throws to stop the
     *                             retries, for example when the document the transaction works on is gone
     * @throws WriteConflictBudgetExceededException when the budget runs out
     */
    <T> T execute(Supplier<T> transaction, Runnable recheckAfterConflict);

    /**
     * For a transaction whose retry needs no re-check: a document it needs that went away fails the transaction
     * itself.
     */
    default <T> T execute(Supplier<T> transaction) {
        return execute(transaction, () -> {
        });
    }

    default void executeWithoutResult(Runnable transaction, Runnable recheckAfterConflict) {
        execute(() -> {
            transaction.run();
            return null;
        }, recheckAfterConflict);
    }
}
