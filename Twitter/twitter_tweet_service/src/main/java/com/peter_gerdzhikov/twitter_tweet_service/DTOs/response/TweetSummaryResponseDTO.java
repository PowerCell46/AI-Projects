package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TweetSummaryResponseDTO {

    private final UUID id;

    private final Instant createdAt;
}
