package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * A page of one author's tweets, newest first. A {@code null} {@code nextCursor} means the end.
 */
@Value
@Builder
public class TweetPageResponseDTO {

    private final String nextCursor;

    private final List<TweetResponseDTO> items;
}
