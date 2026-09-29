package com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.auth;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private UUID id;

    private String username;

    private String email;
}
