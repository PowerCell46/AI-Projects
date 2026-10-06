package com.peter_gerdzhikov.twitter_timeline_service.repositories.savedtweets;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.entities.savedtweets.SavedTweet;
import com.peter_gerdzhikov.twitter_timeline_service.entities.savedtweets.SavedTweetId;

public interface SavedTweetRepository extends JpaRepository<SavedTweet, SavedTweetId> {

    /**
     * Saves the tweet for the user unless they already saved it, so saving again keeps the original
     * {@code savedAt}. Returns the number of rows added: 1, or 0 when the pair already existed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO saved_tweets (user_id, tweet_id, author_id, saved_at)
            VALUES (CAST(:userId AS uuid), CAST(:tweetId AS uuid), CAST(:authorId AS uuid), CAST(:savedAt AS timestamptz))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("tweetId") UUID tweetId,
            @Param("authorId") UUID authorId,
            @Param("savedAt") Instant savedAt
    );

    /**
     * Keyset pages, most recently saved first. Ties on the saved time are broken by tweet id, so a page
     * boundary that falls inside a run of equal timestamps loses and repeats nothing.
     */
    @Query("""
            SELECT s FROM SavedTweet s
            WHERE s.ownerId = :userId
            ORDER BY s.savedAt DESC, s.tweetId DESC
            """)
    List<SavedTweet> findFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT s FROM SavedTweet s
            WHERE s.ownerId = :userId
              AND (s.savedAt < :savedAt OR (s.savedAt = :savedAt AND s.tweetId < :tweetId))
            ORDER BY s.savedAt DESC, s.tweetId DESC
            """)
    List<SavedTweet> findPageAfter(
            @Param("userId") UUID userId,
            @Param("savedAt") Instant savedAt,
            @Param("tweetId") UUID tweetId,
            Pageable limit
    );

    /**
     * The ids among {@code tweetIds} that the user has saved, in no particular order.
     */
    @Query("SELECT s.tweetId FROM SavedTweet s WHERE s.ownerId = :userId AND s.tweetId IN :tweetIds")
    List<UUID> findSavedTweetIds(@Param("userId") UUID userId, @Param("tweetIds") Collection<UUID> tweetIds);

    /**
     * Removes the tweet from the user's saved list. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SavedTweet s WHERE s.ownerId = :userId AND s.tweetId = :tweetId")
    int deleteByUserAndTweet(@Param("userId") UUID userId, @Param("tweetId") UUID tweetId);

    /**
     * Removes the tweet from every saved list. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SavedTweet s WHERE s.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);
}
