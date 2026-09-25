package com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hand-copied wire contract of {@code signal_flow_api_gateway}'s own {@code TopicNewsNotificationEventDTO} -
 * no shared library between the two services. {@code userId} is the dedupe key this service pairs with
 * {@code newsId} in the Redis inbox, stable across an email change unlike {@code emailAddress} itself.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNewsNotificationEventDTO {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DATA_LENGTH = 65_536;

    @NotNull
    private UUID newsId;

    @NotNull
    private UUID interestTopicId;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private String topicName;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private String categoryName;

    @NotNull
    private LocalDate newsDate;

    @NotBlank
    @Size(max = MAX_DATA_LENGTH)
    private String data;

    @NotNull
    private Instant generatedAt;

    @NotNull
    private UUID userId;

    @Email
    @NotBlank
    private String emailAddress;
}
