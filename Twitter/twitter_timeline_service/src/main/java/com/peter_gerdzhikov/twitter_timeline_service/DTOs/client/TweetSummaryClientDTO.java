package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item of the tweet service's {@code GET /internal/v1/tweets/by-author/{authorId}}: the two fields a feed
 * entry needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetSummaryClientDTO {

    private UUID id;

    private Instant createdAt;
}
