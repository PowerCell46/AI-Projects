package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowListItemResponseDTO {

    private UUID id;

    private String bio;

    private String username;

    private boolean followedByMe;

    private String profilePictureUrl;
}
