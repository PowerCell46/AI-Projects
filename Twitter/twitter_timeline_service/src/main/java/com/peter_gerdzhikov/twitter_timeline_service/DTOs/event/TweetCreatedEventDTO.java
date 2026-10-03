package com.peter_gerdzhikov.twitter_timeline_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hand-copied wire contract of the tweet service's {@code tweet.created} event - no shared library between the
 * two services. Only what a feed entry needs is read: the tweet's text and image ids are ignored, because
 * tweets are fetched at read time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetCreatedEventDTO {

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID tweetId;

    @NotNull
    private UUID authorId;

    @NotNull
    private Instant createdAt;
}
