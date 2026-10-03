package com.peter_gerdzhikov.twitter_timeline_service.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.entities.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.entities.FeedEntryId;

public interface FeedEntryRepository extends JpaRepository<FeedEntry, FeedEntryId> {

    /**
     * One multi-row insert of the same tweet into many feeds. Pairs that already exist are skipped, so a
     * redelivered event or a retried fan-out adds nothing. Runs in its own transaction, so a caller paging
     * through followers commits one page at a time. Returns the number of rows actually added.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO feed_entries (user_id, tweet_created_at, tweet_id, author_id)
            SELECT user_id, CAST(:tweetCreatedAt AS timestamptz), CAST(:tweetId AS uuid), CAST(:authorId AS uuid)
            FROM unnest(CAST(:userIds AS uuid[])) AS user_id
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userIds") UUID[] userIds,
            @Param("tweetId") UUID tweetId,
            @Param("authorId") UUID authorId,
            @Param("tweetCreatedAt") Instant tweetCreatedAt
    );

    /**
     * Keyset pages, newest tweet first. Ties on the tweet time are broken by tweet id, so a page boundary that
     * falls inside a run of equal timestamps loses and repeats nothing. The primary key serves the order.
     */
    @Query("""
            SELECT e FROM FeedEntry e
            WHERE e.ownerId = :userId
            ORDER BY e.tweetCreatedAt DESC, e.tweetId DESC
            """)
    List<FeedEntry> findFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT e FROM FeedEntry e
            WHERE e.ownerId = :userId
              AND (e.tweetCreatedAt < :createdAt OR (e.tweetCreatedAt = :createdAt AND e.tweetId < :tweetId))
            ORDER BY e.tweetCreatedAt DESC, e.tweetId DESC
            """)
    List<FeedEntry> findPageAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("tweetId") UUID tweetId,
            Pageable limit
    );

    /**
     * Removes the tweet from every feed. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FeedEntry e WHERE e.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);

    /**
     * Removes what the followee posted up to {@code occurredAt} from the follower's feed. The time bound keeps
     * tweets posted after a quick unfollow and re-follow, even when the event arrives late. Returns the number
     * of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM FeedEntry e
            WHERE e.ownerId = :followerId AND e.authorId = :followeeId AND e.tweetCreatedAt <= :occurredAt
            """)
    int deleteByUnfollow(
            @Param("followerId") UUID followerId,
            @Param("followeeId") UUID followeeId,
            @Param("occurredAt") Instant occurredAt
    );

    /**
     * Removes at most {@code batchSize} entries whose tweet is older than {@code cutoff}, in one statement and
     * one transaction. The caller repeats until a batch removes fewer than {@code batchSize}. Returns the
     * number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM feed_entries
            WHERE ctid IN (
                SELECT ctid FROM feed_entries
                WHERE tweet_created_at < :cutoff
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
