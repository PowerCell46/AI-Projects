package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * A page of the user's saved tweets, most recently saved first. {@code items} can hold fewer than the requested
 * size, because saves whose tweet or author no longer exists are skipped, so only a {@code null}
 * {@code nextCursor} means the end.
 */
@Value
@Builder
public class SavedTweetsResponseDTO {

    private final String nextCursor;

    private final List<TweetItemResponseDTO> items;
}
