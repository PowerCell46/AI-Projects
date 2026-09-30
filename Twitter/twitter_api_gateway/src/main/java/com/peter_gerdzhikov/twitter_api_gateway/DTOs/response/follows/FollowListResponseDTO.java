package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowListResponseDTO {

    private String nextCursor;

    private List<FollowListItemResponseDTO> items;
}
