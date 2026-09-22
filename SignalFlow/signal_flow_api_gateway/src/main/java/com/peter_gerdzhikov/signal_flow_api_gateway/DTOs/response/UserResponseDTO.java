package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private UUID id;

    private String email;

    /**
     * The SPA cannot read the HttpOnly JWT, so this is its only source for role-dependent rendering.
     * Never an authorization input - the gateway authorizes from the token's claims alone.
     */
    private Role role;

    private Instant createdAt;
}
