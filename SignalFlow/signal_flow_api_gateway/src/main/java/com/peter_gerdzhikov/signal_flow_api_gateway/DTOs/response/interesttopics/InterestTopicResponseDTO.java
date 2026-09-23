package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.interesttopics;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A topic as the topic service returns it - read by the gateway, never sent to a client as is.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestTopicResponseDTO {

    private UUID id;

    private String name;

    private String description;

    private UUID categoryId;

    private String categoryName;

    private Instant createdAt;
}
