package com.peter_gerdzhikov.twitter_timeline_service.repositories.likes;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLikeCount;

public interface TweetLikeCountRepository extends JpaRepository<TweetLikeCount, UUID> {

    /**
     * Adds one like to the tweet, creating the counter at 1 when there is none. Returns the number of
     * counters touched.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tweet_like_counts (tweet_id, likes)
            VALUES (CAST(:tweetId AS uuid), 1)
            ON CONFLICT (tweet_id) DO UPDATE SET likes = tweet_like_counts.likes + 1
            """, nativeQuery = true)
    int increment(@Param("tweetId") UUID tweetId);

    /**
     * Removes one like from the tweet's counter. Returns the number of counters touched: 0 when the tweet has
     * none. The table's check refuses to go below 0.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE tweet_like_counts SET likes = likes - 1 WHERE tweet_id = CAST(:tweetId AS uuid)", nativeQuery = true)
    int decrement(@Param("tweetId") UUID tweetId);

    /**
     * Removes the counter of the tweet. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TweetLikeCount c WHERE c.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);
}
