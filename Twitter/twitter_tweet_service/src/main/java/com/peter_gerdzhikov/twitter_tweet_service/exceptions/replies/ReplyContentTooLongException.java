package com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies;

public class ReplyContentTooLongException extends RuntimeException {

    public ReplyContentTooLongException(int maxCodePoints) {
        super("A reply can be at most %d characters.".formatted(maxCodePoints));
    }
}
