package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.categories.CategorySummaryResponseDTO;

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

    private List<CategorySummaryResponseDTO> categories;

    private boolean subscribed;
}
