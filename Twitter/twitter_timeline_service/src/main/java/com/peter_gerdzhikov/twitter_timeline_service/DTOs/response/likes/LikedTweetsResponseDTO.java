package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.likes;

import java.util.List;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

import lombok.Builder;
import lombok.Value;

/**
 * A page of the user's liked tweets, most recently liked first. {@code items} can hold fewer than the requested
 * size, because likes whose tweet or author no longer exists are skipped, so only a {@code null}
 * {@code nextCursor} means the end.
 */
@Value
@Builder
public class LikedTweetsResponseDTO {

    private final String nextCursor;

    private final List<TweetItemResponseDTO> items;
}
