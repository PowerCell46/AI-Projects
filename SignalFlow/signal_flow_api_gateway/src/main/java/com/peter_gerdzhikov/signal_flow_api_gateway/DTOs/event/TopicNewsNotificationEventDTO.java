package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One {@code topic-news.generated} record plus the one subscriber it is being fanned out to.
 * {@code userId} is the dedupe key a downstream email consumer keys on with {@code newsId} - stable
 * across an email change, unlike {@code emailAddress}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNewsNotificationEventDTO {

    private UUID newsId;

    private UUID interestTopicId;

    private String topicName;

    private String categoryName;

    private LocalDate newsDate;

    private String data;

    private Instant generatedAt;

    private UUID userId;

    private String emailAddress;
}
