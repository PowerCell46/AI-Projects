package com.peter_gerdzhikov.twitter_tweet_service.DTOs.client;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item of the gateway's {@code GET /internal/v1/users?ids=}: just what a reply shows of its author.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserClientDTO {

    private UUID id;

    private String username;

    private String profilePictureUrl;
}
