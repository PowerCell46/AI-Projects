package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * Deliberately has no object key: where the bytes live in storage is never exposed.
 */
@Value
@Builder
public class TweetImageResponseDTO {

    private final UUID id;

    private final long sizeBytes;

    private final String contentType;
}
