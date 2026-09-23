package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The topic service's feed page. {@code matching} counts the sent ids that still exist, so a
 * subscription to a deleted topic awaiting reconciliation is never counted.
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
