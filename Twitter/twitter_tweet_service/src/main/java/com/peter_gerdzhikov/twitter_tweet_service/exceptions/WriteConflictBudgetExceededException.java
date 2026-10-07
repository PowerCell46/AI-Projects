package com.peter_gerdzhikov.twitter_tweet_service.exceptions;

/**
 * Concurrent writers kept conflicting with a transaction until its retry budget ran out.
 */
public class WriteConflictBudgetExceededException extends RuntimeException {

    public static final String MESSAGE = "The service is busy, try again.";

    public static final String CODE = "BUSY";

    public WriteConflictBudgetExceededException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
