package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets;

public class TweetIdsOutOfRangeException extends RuntimeException {

    public TweetIdsOutOfRangeException(int maxIds) {
        super("Provide between 1 and %d tweet ids.".formatted(maxIds));
    }
}
