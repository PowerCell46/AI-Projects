package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A copy of {@code signal_flow_interest_topic_service}'s {@code TopicNewsOutboxPublisher} contract - no
 * shared library between the two services, so this shape must be kept in sync by hand.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNewsEventDTO {

    private UUID newsId;

    private UUID interestTopicId;

    private String topicName;

    private String categoryName;

    private LocalDate newsDate;

    private String data;

    private Instant generatedAt;
}
