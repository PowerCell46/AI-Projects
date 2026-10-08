package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.authortweets;

import java.util.List;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

import lombok.Builder;
import lombok.Value;

/**
 * A page of one author's tweets, newest first. {@code items} can hold fewer than the requested size, so only a
 * {@code null} {@code nextCursor} means the end.
 */
@Value
@Builder
public class AuthorTweetsResponseDTO {

    private final String nextCursor;

    private final List<TweetItemResponseDTO> items;
}
