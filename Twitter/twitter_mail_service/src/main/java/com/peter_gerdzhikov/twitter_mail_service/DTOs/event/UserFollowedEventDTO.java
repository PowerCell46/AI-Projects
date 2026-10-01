package com.peter_gerdzhikov.twitter_mail_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hand-copied wire contract of the gateway's own {@code UserFollowedEventDTO} - no shared library between the
 * two services. The validation mirrors the gateway's rules for the same fields. The follower's username goes
 * into the email subject, so its pattern is also what keeps line breaks out of the header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowedEventDTO {

    public static final int MAX_EMAIL_LENGTH = 254;

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID followerId;

    @NotNull
    private UUID followeeId;

    @NotNull
    private Instant occurredAt;

    @Email
    @NotBlank
    @Size(max = MAX_EMAIL_LENGTH)
    @Pattern(regexp = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")
    private String followeeEmail;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,15}$")
    private String followerUsername;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,15}$")
    private String followeeUsername;
}
