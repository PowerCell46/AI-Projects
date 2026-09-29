package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendConfirmationRequestDTO {

    @Email
    @NotBlank
    @Size(max = 254)
    private String email;
}
