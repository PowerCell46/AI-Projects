package com.peter_gerdzhikov.twitter_api_gateway.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * The {@code user.followed} payload. The field names are the contract the mail service reads, so renaming
 * one is a breaking change.
 */
@Value
@Builder
public class UserFollowedEventDTO {

    private final UUID eventId;

    private final UUID followerId;

    private final UUID followeeId;

    private final Instant occurredAt;

    private final String followeeEmail;

    private final String followerUsername;

    private final String followeeUsername;
}
