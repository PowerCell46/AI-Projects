package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The tweet service's {@code GET /internal/v1/tweets/by-author/{authorId}/page}. The cursor is the tweet
 * service's own and is handed on to the next call untouched.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetPageClientDTO {

    private String nextCursor;

    private List<TweetClientDTO> items;
}
