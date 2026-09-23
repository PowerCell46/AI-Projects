package com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code nextCursor} is null once the last page is served. {@code total} counts every topic, and
 * {@code matching} counts the requested ids that still exist - whatever the mode.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestTopicFeedResponseDTO {

    private List<InterestTopicResponseDTO> items;

    private String nextCursor;

    private long total;

    private long matching;
}
