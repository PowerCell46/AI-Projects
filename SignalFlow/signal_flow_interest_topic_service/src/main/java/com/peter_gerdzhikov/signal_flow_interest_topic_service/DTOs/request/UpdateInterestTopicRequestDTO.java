package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update - a null field leaves that column unchanged, so none of the fields are required. A
 * present name or prompt must still have text, as on create.
 */
@Data
@NoArgsConstructor
public class UpdateInterestTopicRequestDTO {

    private static final String NOT_BLANK_REGEX = "(?s).*\\S.*";

    @Size(max = 100)
    @Pattern(regexp = NOT_BLANK_REGEX, message = "must not be blank")
    private String name;

    @Size(max = 1000)
    private String description;

    @Size(max = 4000)
    @Pattern(regexp = NOT_BLANK_REGEX, message = "must not be blank")
    private String prompt;

    @Size(min = 1, max = InterestTopic.MAX_CATEGORIES)
    private List<@NotNull UUID> categoryIds;

    private NewsSchedule schedule;
}
