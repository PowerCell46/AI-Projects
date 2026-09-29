package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    @Email
    @NotBlank
    @Size(max = 254)
    @Pattern(regexp = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$", message = "must be a valid email address")
    private String email;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,15}$", message = "must be 3 to 15 letters, digits or underscores")
    private String username;

    @NotBlank
    @MaxUtf8Bytes(72)
    @Size(min = 8, max = 72)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "must contain a lowercase letter, an uppercase letter and a digit"
    )
    private String password;
}
