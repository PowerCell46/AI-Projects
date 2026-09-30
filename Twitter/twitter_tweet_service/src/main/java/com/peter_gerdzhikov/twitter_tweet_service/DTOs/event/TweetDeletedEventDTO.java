package com.peter_gerdzhikov.twitter_tweet_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * The {@code tweet.deleted} payload. The field names are the contract consumers read, so renaming one is a
 * breaking change.
 */
@Value
@Builder
public class TweetDeletedEventDTO {

    private final UUID eventId;

    private final UUID tweetId;

    private final UUID authorId;

    private final Instant deletedAt;
}
