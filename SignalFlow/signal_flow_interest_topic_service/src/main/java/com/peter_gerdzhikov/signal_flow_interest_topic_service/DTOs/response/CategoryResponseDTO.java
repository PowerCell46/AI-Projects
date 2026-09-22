package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponseDTO {

    private UUID id;

    private String name;

    private Instant createdAt;
}
