package com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets;

public class TooManyImagesException extends RuntimeException {

    public TooManyImagesException(int maxImages) {
        super("A tweet can have at most %d images.".formatted(maxImages));
    }
}
