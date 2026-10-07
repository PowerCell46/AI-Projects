package com.peter_gerdzhikov.twitter_tweet_service.repositories.replies;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;

public interface ReplyRepositoryCustom {

    /**
     * The tweet's oldest replies, oldest first (ties by id), at most {@code limit}.
     */
    List<Reply> findFirstPage(UUID tweetId, int limit);

    /**
     * The tweet's replies after the keyset position {@code (afterCreatedAt, afterId)}, oldest first (ties by id),
     * at most {@code limit}.
     */
    List<Reply> findPageAfter(UUID tweetId, Instant afterCreatedAt, UUID afterId, int limit);

    /**
     * Sets the content, {@code edited} and {@code updatedAt} only when the reply exists, belongs to the tweet
     * and was written by the author.
     *
     * @return whether a reply matched
     */
    boolean updateContentIfAuthor(UUID id, UUID tweetId, UUID authorId, String content, Instant updatedAt);

    /**
     * Deletes the reply only when it belongs to the tweet.
     *
     * @return whether a reply was deleted
     */
    boolean deleteByIdAndTweetId(UUID id, UUID tweetId);

    /**
     * Deletes every reply of the tweet.
     *
     * @return how many replies were deleted
     */
    long deleteAllByTweetId(UUID tweetId);
}
