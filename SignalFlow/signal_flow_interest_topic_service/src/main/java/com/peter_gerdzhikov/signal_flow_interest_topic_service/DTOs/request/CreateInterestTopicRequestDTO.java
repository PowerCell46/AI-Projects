package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    private UUID categoryId;
}
