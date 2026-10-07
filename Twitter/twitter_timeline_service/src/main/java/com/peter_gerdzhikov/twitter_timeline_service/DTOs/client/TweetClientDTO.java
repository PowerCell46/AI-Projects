package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item of the tweet service's {@code GET /internal/v1/tweets?ids=}. Only the fields a feed item shows are
 * read; anything else the tweet service sends is ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetClientDTO {

    private UUID id;

    private UUID authorId;

    private String content;

    /**
     * A wrapper, because Jackson refuses a missing primitive; a tweet service that predates replies sends none.
     */
    private Long replyCount;

    private Instant createdAt;

    private Instant updatedAt;

    private List<TweetImageClientDTO> images;
}
