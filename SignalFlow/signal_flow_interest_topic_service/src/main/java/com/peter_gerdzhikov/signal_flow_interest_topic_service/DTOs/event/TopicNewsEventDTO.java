package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

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

    private List<String> categoryNames;

    private LocalDate newsDate;

    private NewsSchedule schedule;

    private LocalDate periodStart;

    private String data;

    private Instant generatedAt;
}
