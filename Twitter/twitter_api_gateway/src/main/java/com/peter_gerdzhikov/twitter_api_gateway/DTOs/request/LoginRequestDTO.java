package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.MaxUtf8Bytes;

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
public class LoginRequestDTO {

    // TODO: Add a javadoc explaining that's email or username
    @NotBlank
    @Size(max = 254)
    private String identifier;

    @NotBlank
    @MaxUtf8Bytes(72)
    @Size(max = 72)
    private String password;
}
