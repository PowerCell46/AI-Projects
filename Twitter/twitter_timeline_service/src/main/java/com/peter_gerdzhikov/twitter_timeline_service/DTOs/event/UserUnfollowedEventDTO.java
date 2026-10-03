package com.peter_gerdzhikov.twitter_timeline_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hand-copied wire contract of the gateway's {@code user.unfollowed} event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUnfollowedEventDTO {

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID followerId;

    @NotNull
    private UUID followeeId;

    @NotNull
    private Instant occurredAt;
}
