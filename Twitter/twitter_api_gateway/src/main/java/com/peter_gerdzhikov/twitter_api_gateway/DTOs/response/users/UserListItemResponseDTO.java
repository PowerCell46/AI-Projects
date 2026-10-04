package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListItemResponseDTO {

    private UUID id;

    private String bio;

    private String username;

    private long followersCount;

    private boolean followedByMe;

    private String profilePictureUrl;
}
