package com.peter_gerdzhikov.twitter_timeline_service.repositories.views;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetViewCount;

public interface TweetViewCountRepository extends JpaRepository<TweetViewCount, UUID> {

    /**
     * Adds one view to each tweet, creating the counter at 1 when there is none. The ids must be distinct:
     * Postgres refuses to update one row twice in a statement. They are upserted in ascending order, so two
     * overlapping calls take their row locks in the same sequence and can't deadlock. Returns the number of
     * counters touched.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tweet_view_counts (tweet_id, views)
            SELECT tweet_id, 1
            FROM unnest(CAST(:tweetIds AS uuid[])) AS tweet_id
            ORDER BY tweet_id
            ON CONFLICT (tweet_id) DO UPDATE SET views = tweet_view_counts.views + 1
            """, nativeQuery = true)
    int incrementAll(@Param("tweetIds") UUID[] tweetIds);

    /**
     * Removes the counter of the tweet. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TweetViewCount c WHERE c.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);
}
