package com.peter_gerdzhikov.twitter_timeline_service.entities;

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
 * One tweet in one user's feed. Rows are only inserted (by the native statement in {@code FeedEntryRepository},
 * which is why there is no generator) or deleted, never edited, so the natural key is the primary key. It is
 * ordered {@code (user_id, tweet_created_at, tweet_id)} and doubles as the page index. Hibernate orders the key
 * columns by attribute name, which is why the user attribute is {@code ownerId} (before {@code tweetCreatedAt}
 * and {@code tweetId}) while its column stays {@code user_id}. A redelivered
 * {@code tweet.created} carries the same {@code tweetCreatedAt}, so it still lands on the same key.
 * {@code tweetCreatedAt} must be truncated to microseconds by the caller so it round-trips through Postgres
 * exactly, because the page cursor is built from it.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(FeedEntryId.class)
@Table(
        name = "feed_entries",
        indexes = {
                @Index(name = "ix_feed_entries_tweet", columnList = "tweet_id"),
                @Index(name = "ix_feed_entries_user_author", columnList = "user_id, author_id"),
                @Index(name = "ix_feed_entries_tweet_created", columnList = "tweet_created_at")
        }
)
public class FeedEntry {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID ownerId;

    @Id
    @Column(name = "tweet_id", updatable = false, nullable = false)
    private UUID tweetId;

    @Column(name = "author_id", updatable = false, nullable = false)
    private UUID authorId;

    @Id
    @Column(name = "tweet_created_at", updatable = false, nullable = false)
    private Instant tweetCreatedAt;
}
