package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes;

import java.time.Instant;
import java.util.UUID;

public interface LikeRecordingService {

    /**
     * Records the like and, only when it was new, bumps the tweet's counter, in one transaction, so the counter
     * can never disagree with the like rows.
     *
     * @param authorId the author of a tweet that exists
     * @param likedAt  truncated to microseconds
     * @return {@code true} when the tweet was new to the user
     */
    boolean like(UUID userId, UUID tweetId, UUID authorId, Instant likedAt);

    /**
     * Removes the like and, only when there was one, lowers the tweet's counter, in one transaction.
     *
     * @return {@code true} when the user had liked the tweet
     */
    boolean unlike(UUID userId, UUID tweetId);
}
