package com.wealthbuilder.backend.DTOs.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Create/edit payload for an asset. {@code imageBase64} must be a {@code data:} image URI;
 * the pattern keeps non-image or non-base64 payloads out of the database.
 */
@Getter
@Setter
@NoArgsConstructor
public class AssetRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String description;

    @NotBlank
    @Pattern(
            regexp = "^data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\s]+$",
            message = "must be a data:image/...;base64,... URI")
    private String imageBase64;

    @NotBlank
    @Size(max = 255)
    private String imageName;
}
