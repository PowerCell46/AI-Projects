package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedCountsResponseDTO {

    private long all;

    private long subscribed;

    private long notSubscribed;
}
