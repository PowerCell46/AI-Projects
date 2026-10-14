package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateInterestTopicRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 4000)
    private String prompt;

    @NotNull
    @Size(min = 1, max = InterestTopic.MAX_CATEGORIES)
    private List<@NotNull UUID> categoryIds;

    private NewsSchedule schedule;
}
