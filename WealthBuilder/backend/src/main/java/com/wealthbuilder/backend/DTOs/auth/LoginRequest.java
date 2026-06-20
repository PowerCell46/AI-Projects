package com.wealthbuilder.backend.DTOs.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Login credentials. No {@code @ToString} is generated so the raw password is never
 * logged.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
