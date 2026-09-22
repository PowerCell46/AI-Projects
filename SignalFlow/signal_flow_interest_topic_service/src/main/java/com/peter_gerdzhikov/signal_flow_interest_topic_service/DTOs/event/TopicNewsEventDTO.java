package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
