package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.interesttopics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.categories.CategorySummaryResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

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

    private List<CategorySummaryResponseDTO> categories;

    private NewsSchedule schedule;

    private Instant createdAt;
}
