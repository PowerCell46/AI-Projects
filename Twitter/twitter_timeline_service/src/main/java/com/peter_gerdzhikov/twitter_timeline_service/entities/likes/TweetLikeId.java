package com.peter_gerdzhikov.twitter_timeline_service.entities.likes;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The primary key of {@link TweetLike}. The field names must match the entity's {@code @Id} fields.
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TweetLikeId implements Serializable {

    private UUID ownerId;

    private UUID tweetId;
}
