package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.reads;

public class TweetLimitOutOfRangeException extends RuntimeException {

    public TweetLimitOutOfRangeException(int maxLimit) {
        super("Provide a limit between 1 and %d.".formatted(maxLimit));
    }
}
