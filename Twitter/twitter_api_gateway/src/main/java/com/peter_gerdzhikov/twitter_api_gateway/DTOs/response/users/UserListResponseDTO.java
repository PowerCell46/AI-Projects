package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponseDTO {

    private String nextCursor;

    private List<UserListItemResponseDTO> items;
}
