package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content;

public class TweetContentTooLongException extends RuntimeException {

    public TweetContentTooLongException(int maxCodePoints) {
        super("A tweet can be at most %d characters.".formatted(maxCodePoints));
    }
}
