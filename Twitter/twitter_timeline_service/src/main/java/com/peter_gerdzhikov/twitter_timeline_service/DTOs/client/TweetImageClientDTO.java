package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetImageClientDTO {

    private UUID id;

    private long sizeBytes;

    private String contentType;
}
