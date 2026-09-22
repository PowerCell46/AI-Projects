package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestTopicResponseDTO {

    private UUID id;

    private String name;

    private String description;

    private String prompt;

    private UUID categoryId;

    private String categoryName;

    private Instant createdAt;
}
