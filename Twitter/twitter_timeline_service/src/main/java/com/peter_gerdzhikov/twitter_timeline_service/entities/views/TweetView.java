package com.peter_gerdzhikov.twitter_timeline_service.entities.views;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One viewer who saw one tweet. Rows are only inserted (by the native statement in {@code TweetViewRepository},
 * which is why there is no generator) or deleted, never edited, so the natural pair is the primary key. It has
 * no timestamp: nothing reads one. Hibernate orders the key columns by attribute name, so the key is
 * {@code (tweet_id, viewer_id)} and also serves the delete by tweet.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TweetViewId.class)
@Table(name = "tweet_views")
public class TweetView {

    @Id
    @Column(name = "tweet_id", updatable = false, nullable = false)
    private UUID tweetId;

    @Id
    @Column(name = "viewer_id", updatable = false, nullable = false)
    private UUID viewerId;
}
