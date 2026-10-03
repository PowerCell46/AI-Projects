package com.peter_gerdzhikov.twitter_timeline_service.DTOs.event;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hand-copied wire contract of the tweet service's {@code tweet.deleted} event. Only the tweet id is acted on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetDeletedEventDTO {

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID tweetId;
}
