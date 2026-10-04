package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRequestDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "must be a 43-character confirmation token")
    private String token;
}
