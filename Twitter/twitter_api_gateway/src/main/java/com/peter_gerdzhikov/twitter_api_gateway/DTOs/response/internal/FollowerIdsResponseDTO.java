package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FollowerIdsResponseDTO {

    private final List<UUID> ids;

    private final String nextCursor;
}
