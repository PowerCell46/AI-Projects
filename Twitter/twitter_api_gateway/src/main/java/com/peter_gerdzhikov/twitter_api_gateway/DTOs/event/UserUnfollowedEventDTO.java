package com.peter_gerdzhikov.twitter_api_gateway.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * The {@code user.unfollowed} payload. The field names are the contract the timeline service reads, so renaming
 * one is a breaking change. It carries no email or username: the consumer only needs the pair and the time.
 */
@Value
@Builder
public class UserUnfollowedEventDTO {

    private final UUID eventId;

    private final UUID followerId;

    private final UUID followeeId;

    private final Instant occurredAt;
}
