package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponseDTO {

    private UUID id;

    private UUID interestTopicId;

    private Instant createdAt;
}
