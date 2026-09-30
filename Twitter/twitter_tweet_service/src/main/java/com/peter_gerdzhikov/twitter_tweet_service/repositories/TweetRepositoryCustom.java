package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;

public interface TweetRepositoryCustom {

    /**
     * Atomically adds one view and returns the tweet as it is after the increment, or empty when it doesn't exist.
     */
    Optional<Tweet> findAndIncrementViews(UUID id);

    /**
     * Sets the content and {@code updatedAt} only when the tweet exists and belongs to the author.
     *
     * @return whether a tweet matched
     */
    boolean updateContentIfAuthor(UUID id, UUID authorId, String content, Instant updatedAt);

    /**
     * Deletes the tweet only when it exists and belongs to the author.
     *
     * @return whether a tweet was deleted
     */
    boolean deleteIfAuthor(UUID id, UUID authorId);
}
