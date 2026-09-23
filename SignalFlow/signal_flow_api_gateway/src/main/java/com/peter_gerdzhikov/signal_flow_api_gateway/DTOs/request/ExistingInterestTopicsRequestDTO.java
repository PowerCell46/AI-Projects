package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The body the gateway sends to the topic service's existence lookup - outbound, unlike the other
 * request DTOs, which the gateway receives.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExistingInterestTopicsRequestDTO {

    private List<UUID> ids;
}
