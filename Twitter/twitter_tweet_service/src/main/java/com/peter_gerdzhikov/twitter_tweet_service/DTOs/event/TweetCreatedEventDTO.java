package com.peter_gerdzhikov.twitter_tweet_service.DTOs.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * The {@code tweet.created} payload. The field names are the contract consumers read, so renaming one is a
 * breaking change.
 */
@Value
@Builder
public class TweetCreatedEventDTO {

    private final UUID eventId;

    private final UUID tweetId;

    private final UUID authorId;

    private final String content;

    private final Instant createdAt;

    private final List<UUID> imageIds;
}
