package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubscribeRequestDTO {

    @NotNull
    private UUID interestTopicId;
}
