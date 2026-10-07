package com.peter_gerdzhikov.twitter_tweet_service.utilities.retry;

/**
 * Time that only moves forward, for measuring how long something has taken. It is not the {@code Clock} bean,
 * which tests freeze, so a test drives this one by hand.
 */
@FunctionalInterface
public interface MonotonicTimeSource {

    long nanoTime();
}
