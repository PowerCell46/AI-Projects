package com.peter_gerdzhikov.twitter_timeline_service.entities;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The primary key of {@link FeedEntry}. The field names must match the entity's {@code @Id} fields.
 */
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class FeedEntryId implements Serializable {

    private UUID ownerId;

    private UUID tweetId;

    private Instant tweetCreatedAt;
}
