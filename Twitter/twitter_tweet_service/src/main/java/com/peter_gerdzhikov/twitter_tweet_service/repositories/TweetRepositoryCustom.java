package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import java.time.Instant;
import java.util.UUID;

public interface TweetRepositoryCustom {

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
