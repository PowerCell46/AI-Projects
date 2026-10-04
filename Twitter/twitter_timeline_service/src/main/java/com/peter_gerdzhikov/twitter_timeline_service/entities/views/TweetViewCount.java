package com.peter_gerdzhikov.twitter_timeline_service.entities.views;

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
 * The number of unique viewers of one tweet. The count is {@code updatable = false}: only the atomic upsert in
 * {@code TweetViewCountRepository} changes it, so a flush can never write back a stale value.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tweet_view_counts")
@Check(name = TweetViewCount.VIEWS_CONSTRAINT, constraints = "views >= 0")
public class TweetViewCount {

    public static final String VIEWS_CONSTRAINT = "ck_tweet_view_counts_views_non_negative";

    @Id
    @Column(name = "tweet_id", updatable = false, nullable = false)
    private UUID tweetId;

    @Column(name = "views", updatable = false, nullable = false)
    private long views;
}
