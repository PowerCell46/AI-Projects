package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReplyResponseDTO {

    private final UUID id;

    private final UUID tweetId;

    private final String content;

    private final boolean edited;

    private final Instant createdAt;

    private final Instant updatedAt;

    private final AuthorResponseDTO author;
}
