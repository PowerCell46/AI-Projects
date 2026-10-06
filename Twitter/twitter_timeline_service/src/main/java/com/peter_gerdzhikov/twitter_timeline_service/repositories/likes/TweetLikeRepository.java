package com.peter_gerdzhikov.twitter_timeline_service.repositories.likes;

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

import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLike;
import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLikeId;

public interface TweetLikeRepository extends JpaRepository<TweetLike, TweetLikeId> {

    /**
     * Likes the tweet for the user unless they already liked it, so liking again keeps the original
     * {@code likedAt}. Returns the number of rows added: 1, or 0 when the pair already existed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tweet_likes (user_id, tweet_id, author_id, liked_at)
            VALUES (CAST(:userId AS uuid), CAST(:tweetId AS uuid), CAST(:authorId AS uuid), CAST(:likedAt AS timestamptz))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("tweetId") UUID tweetId,
            @Param("authorId") UUID authorId,
            @Param("likedAt") Instant likedAt
    );

    /**
     * Keyset pages, most recently liked first. Ties on the liked time are broken by tweet id, so a page
     * boundary that falls inside a run of equal timestamps loses and repeats nothing.
     */
    @Query("""
            SELECT l FROM TweetLike l
            WHERE l.ownerId = :userId
            ORDER BY l.likedAt DESC, l.tweetId DESC
            """)
    List<TweetLike> findFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT l FROM TweetLike l
            WHERE l.ownerId = :userId
              AND (l.likedAt < :likedAt OR (l.likedAt = :likedAt AND l.tweetId < :tweetId))
            ORDER BY l.likedAt DESC, l.tweetId DESC
            """)
    List<TweetLike> findPageAfter(
            @Param("userId") UUID userId,
            @Param("likedAt") Instant likedAt,
            @Param("tweetId") UUID tweetId,
            Pageable limit
    );

    /**
     * The ids among {@code tweetIds} that the user has liked, in no particular order.
     */
    @Query("SELECT l.tweetId FROM TweetLike l WHERE l.ownerId = :userId AND l.tweetId IN :tweetIds")
    List<UUID> findLikedTweetIds(@Param("userId") UUID userId, @Param("tweetIds") Collection<UUID> tweetIds);

    /**
     * Removes the user's like of the tweet. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TweetLike l WHERE l.ownerId = :userId AND l.tweetId = :tweetId")
    int deleteByUserAndTweet(@Param("userId") UUID userId, @Param("tweetId") UUID tweetId);

    /**
     * Removes every like of the tweet. Idempotent. Returns the number of rows removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TweetLike l WHERE l.tweetId = :tweetId")
    int deleteByTweetId(@Param("tweetId") UUID tweetId);
}
