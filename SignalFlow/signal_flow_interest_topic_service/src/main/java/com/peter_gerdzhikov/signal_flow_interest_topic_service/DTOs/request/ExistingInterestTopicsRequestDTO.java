package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExistingInterestTopicsRequestDTO {

    @NotNull
    private List<@NotNull UUID> ids;
}
