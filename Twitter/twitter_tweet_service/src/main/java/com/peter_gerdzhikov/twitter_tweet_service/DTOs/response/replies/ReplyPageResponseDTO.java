package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * A page of replies. {@code items} can hold fewer than the requested size, because a reply whose author the
 * gateway no longer knows is left out, so only a {@code null} {@code nextCursor} means the end.
 */
@Value
@Builder
public class ReplyPageResponseDTO {

    private final String nextCursor;

    private final List<ReplyResponseDTO> items;
}
