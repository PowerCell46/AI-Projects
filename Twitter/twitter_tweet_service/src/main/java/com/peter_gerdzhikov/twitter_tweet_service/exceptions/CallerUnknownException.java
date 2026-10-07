package com.peter_gerdzhikov.twitter_tweet_service.exceptions;

/**
 * The gateway knows no user for the caller's {@code X-User-Id}, so a write that names the caller is refused.
 */
public class CallerUnknownException extends RuntimeException {

    public static final String MESSAGE = "The caller is not a known user.";

    public static final String CODE = "CALLER_UNKNOWN";

    public CallerUnknownException() {
        super(MESSAGE);
    }
}
