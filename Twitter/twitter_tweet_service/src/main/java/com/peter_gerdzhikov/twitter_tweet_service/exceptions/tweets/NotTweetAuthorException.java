package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets;

public class NotTweetAuthorException extends RuntimeException {

    public static final String EDIT_MESSAGE = "You can only edit your own tweets.";

    public static final String DELETE_MESSAGE = "You can only delete your own tweets.";

    public NotTweetAuthorException(String message) {
        super(message);
    }
}
