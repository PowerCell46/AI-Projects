package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.InterestTopicFeedMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The body the gateway sends to the topic service's feed page - outbound, like
 * {@link ExistingInterestTopicsRequestDTO}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestTopicFeedRequestDTO {

    private List<UUID> ids;

    private InterestTopicFeedMode mode;

    private String after;

    private int size;
}
