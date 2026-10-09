package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.reads;

import java.time.Instant;

public class TweetSinceOutOfRangeException extends RuntimeException {

    public TweetSinceOutOfRangeException(Instant earliest, Instant latest) {
        super("Provide a since between %s and %s.".formatted(earliest, latest));
    }
}
