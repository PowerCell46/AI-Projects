package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TweetResponseDTO {

    private final UUID id;

    private final UUID authorId;

    private final String content;

    private final long replyCount;

    private final Instant createdAt;

    private final Instant updatedAt;

    private final List<TweetImageResponseDTO> images;
}
