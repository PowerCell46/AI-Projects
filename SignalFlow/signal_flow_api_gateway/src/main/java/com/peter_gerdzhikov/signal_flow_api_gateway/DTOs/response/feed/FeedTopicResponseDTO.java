package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedTopicResponseDTO {

    private UUID id;

    private String name;

    private String description;

    private UUID categoryId;

    private String categoryName;

    private boolean subscribed;
}
