package com.peter_gerdzhikov.twitter_timeline_service.DTOs.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TweetImageResponseDTO {

    private final UUID id;

    private final long sizeBytes;

    private final String contentType;
}
