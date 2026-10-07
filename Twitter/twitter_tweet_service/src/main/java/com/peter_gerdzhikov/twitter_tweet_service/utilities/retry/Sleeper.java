package com.peter_gerdzhikov.twitter_tweet_service.utilities.retry;

import java.time.Duration;

/**
 * Pauses the calling thread, so a test can record the pauses instead of waiting them out.
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
