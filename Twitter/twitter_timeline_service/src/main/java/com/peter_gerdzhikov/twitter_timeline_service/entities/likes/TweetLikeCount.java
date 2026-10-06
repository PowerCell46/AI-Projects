package com.peter_gerdzhikov.twitter_timeline_service.entities.likes;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The number of likes of one tweet, equal to the tweet's {@code tweet_likes} rows. The count is
 * {@code updatable = false}: only the atomic statements in {@code TweetLikeCountRepository} change it, so a
 * flush can never write back a stale value.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tweet_like_counts")
@Check(name = TweetLikeCount.LIKES_CONSTRAINT, constraints = "likes >= 0")
public class TweetLikeCount {

    public static final String LIKES_CONSTRAINT = "ck_tweet_like_counts_likes_non_negative";

    @Id
    @Column(name = "tweet_id", updatable = false, nullable = false)
    private UUID tweetId;

    @Column(name = "likes", updatable = false, nullable = false)
    private long likes;
}
