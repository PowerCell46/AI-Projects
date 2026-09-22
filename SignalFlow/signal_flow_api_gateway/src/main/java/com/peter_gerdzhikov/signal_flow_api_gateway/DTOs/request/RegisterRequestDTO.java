package com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequestDTO {

    @Email
    @NotBlank
    @Size(max = 254)
    private String email; // ? No regex

    @NotBlank
    @Size(min = 8, max = 72)
    private String password; // ? No regex
}
