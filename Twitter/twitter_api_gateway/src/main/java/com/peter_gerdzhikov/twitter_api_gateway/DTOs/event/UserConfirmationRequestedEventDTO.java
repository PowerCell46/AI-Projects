package com.peter_gerdzhikov.twitter_api_gateway.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * The {@code user.confirmation-requested} payload. The field names are the contract the mail service
 * reads, so renaming one is a breaking change.
 */
@Value
@Builder
public class UserConfirmationRequestedEventDTO {

    private final UUID eventId;

    private final UUID userId;

    private final String email;

    private final String username;

    private final String confirmationUrl;

    private final Instant expiresAt;
}
