package com.peter_gerdzhikov.twitter_timeline_service.entities.likes;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One tweet one user liked. Rows are only inserted (by the native statement in {@code TweetLikeRepository},
 * which is why there is no generator) or deleted, never edited, so the natural pair is the primary key. The
 * user attribute is {@code ownerId} to match {@code SavedTweet}; its column stays {@code user_id}.
 * {@code likedAt} must be truncated to microseconds by the caller so it round-trips through Postgres exactly,
 * because the page cursor is built from it.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TweetLikeId.class)
@Table(
        name = "tweet_likes",
        indexes = {
                @Index(name = "ix_tweet_likes_tweet", columnList = "tweet_id"),
                @Index(name = "ix_tweet_likes_user_liked", columnList = "user_id, liked_at, tweet_id")
        }
)
public class TweetLike {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID ownerId;

    @Id
    @Column(name = "tweet_id", updatable = false, nullable = false)
    private UUID tweetId;

    @Column(name = "author_id", updatable = false, nullable = false)
    private UUID authorId;

    @Column(name = "liked_at", updatable = false, nullable = false)
    private Instant likedAt;
}
