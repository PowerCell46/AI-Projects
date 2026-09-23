package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.interesttopics;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The topic service's answer to the existence lookup - inbound, unlike the other response DTOs, which
 * the gateway returns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExistingInterestTopicsResponseDTO {

    private List<UUID> existingIds;
}
