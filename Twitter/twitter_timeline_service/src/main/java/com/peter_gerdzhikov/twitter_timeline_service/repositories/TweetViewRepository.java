package com.peter_gerdzhikov.twitter_timeline_service.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetView;
import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetViewId;

public interface TweetViewRepository extends JpaRepository<TweetView, TweetViewId> {

    /**
     * Records the viewer for each tweet unless they already viewed it, and returns the ids of the tweets that
     * were new to them, in ascending order. The ids are inserted in that same order, so two reports that
     * overlap take their locks in the same sequence and can't deadlock. Meant to run in the transaction of the
     * caller that then counts the returned ids.
     */
    @Query(value = """
            INSERT INTO tweet_views (tweet_id, viewer_id)
            SELECT tweet_id, CAST(:viewerId AS uuid)
            FROM unnest(CAST(:tweetIds AS uuid[])) AS tweet_id
            ORDER BY tweet_id
            ON CONFLICT DO NOTHING
            RETURNING tweet_id
            """, nativeQuery = true)
    List<UUID> insertIfAbsent(@Param("viewerId") UUID viewerId, @Param("tweetIds") UUID[] tweetIds);

    /**
     * Removes every viewer of the tweet. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TweetView v WHERE v.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);
}
