package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthorResponseDTO {

    private final UUID id;

    private final String username;

    private final String profilePictureUrl;
}
