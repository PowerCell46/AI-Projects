package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FollowCheckResponseDTO {

    private final boolean following;
}
