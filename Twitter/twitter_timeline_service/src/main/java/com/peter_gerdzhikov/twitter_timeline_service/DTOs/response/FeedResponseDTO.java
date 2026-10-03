package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * A page of the feed. {@code items} can hold fewer than the requested size, because entries whose tweet or
 * author no longer exists are skipped, so only a {@code null} {@code nextCursor} means the end.
 */
@Value
@Builder
public class FeedResponseDTO {

    private final String nextCursor;

    private final List<TweetItemResponseDTO> items;
}
