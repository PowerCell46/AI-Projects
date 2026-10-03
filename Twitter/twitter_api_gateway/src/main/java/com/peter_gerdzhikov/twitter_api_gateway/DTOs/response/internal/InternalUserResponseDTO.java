package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InternalUserResponseDTO {

    private final UUID id;

    private final String username;

    private final String profilePictureUrl;
}
