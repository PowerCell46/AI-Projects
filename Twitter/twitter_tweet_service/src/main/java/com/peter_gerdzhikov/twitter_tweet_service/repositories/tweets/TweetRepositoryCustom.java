package com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;

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

    /**
     * Reads the author's tweets created at or after {@code since}, newest first (ties by id, descending). Only
     * {@code id} and {@code createdAt} are loaded; every other field of the returned tweets is empty.
     */
    List<Tweet> findNewestByAuthorSince(UUID authorId, Instant since, int limit);

    /**
     * Adds {@code delta} to {@code replyCount} and leaves {@code updatedAt} alone, so a reply never makes the tweet
     * look edited.
     *
     * @return whether a tweet matched
     */
    boolean incrementReplyCount(UUID id, long delta);
}
