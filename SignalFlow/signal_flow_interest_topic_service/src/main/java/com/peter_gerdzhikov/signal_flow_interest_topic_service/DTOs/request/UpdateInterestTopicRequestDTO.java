package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.UUID;

import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update - a null field leaves that column unchanged, so none of the fields are required.
 */
@Data
@NoArgsConstructor
public class UpdateInterestTopicRequestDTO {

    @Size(max = 100)
    private String name;

    @Size(max = 1000)
    private String description;

    @Size(max = 4000)
    private String prompt;

    private UUID categoryId;
}
