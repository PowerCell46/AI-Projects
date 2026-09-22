package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoryRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String name;
}
