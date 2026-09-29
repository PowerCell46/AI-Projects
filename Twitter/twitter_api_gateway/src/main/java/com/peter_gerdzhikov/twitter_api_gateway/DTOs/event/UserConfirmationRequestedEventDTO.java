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

    UUID eventId;

    UUID userId;

    String email;

    String username;

    String confirmationUrl;

    Instant expiresAt;
}
// ? No access modifiers?