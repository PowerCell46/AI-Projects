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
 * Hand-copied wire contract of the gateway's own {@code UserConfirmationRequestedEventDTO} - no shared library
 * between the two services. The validation mirrors the gateway's rules for the same fields, so this service
 * accepts exactly the addresses and usernames the gateway lets through.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConfirmationRequestedEventDTO {

    public static final int MAX_EMAIL_LENGTH = 254;

    public static final int MAX_CONFIRMATION_URL_LENGTH = 2048;

    @NotNull
    private UUID userId;

    @NotNull
    private UUID eventId;

    @Email
    @NotBlank
    @Size(max = MAX_EMAIL_LENGTH)
    @Pattern(regexp = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")
    private String email;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,15}$")
    private String username;

    @NotNull
    private Instant expiresAt;

    @NotBlank
    @Size(max = MAX_CONFIRMATION_URL_LENGTH)
    private String confirmationUrl;
}
