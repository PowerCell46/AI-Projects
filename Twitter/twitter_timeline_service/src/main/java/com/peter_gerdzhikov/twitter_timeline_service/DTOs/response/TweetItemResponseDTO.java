package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * One tweet in a list: the tweet as the tweet service holds it, its author, the number of unique viewers, whether the
 * caller saved it, its public like count and whether the caller liked it, and how many replies it has. The image bytes stay at the tweet
 * service's own route; only their ids and types are listed here.
 */
@Value
@Builder
public class TweetItemResponseDTO {

    private final UUID id;

    private final long views;

    private final boolean savedByMe;

    private final long likes;

    private final boolean likedByMe;

    private final long replyCount;

    private final String content;

    private final Instant createdAt;

    private final Instant updatedAt;

    private final AuthorResponseDTO author;

    private final List<TweetImageResponseDTO> images;
}
