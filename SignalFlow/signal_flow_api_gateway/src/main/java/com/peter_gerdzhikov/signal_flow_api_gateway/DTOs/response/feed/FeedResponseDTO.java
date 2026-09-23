package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code nextCursor} is the {@code after} for the next page, and null once everything is loaded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedResponseDTO {

    private List<FeedTopicResponseDTO> items;

    private String nextCursor;

    private FeedCountsResponseDTO counts;
}
