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

    // Null on create. On update the moderator client echoes back the version it received so
    // the service can detect a stale-form submission before Hibernate's own commit-time check.
    private Long version;

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    // A 10 MB image base64-encodes to ~4/3 its size; the +64 covers the `data:image/...;base64,`
    // prefix and padding. Bounds the field so an oversized image is rejected, not buffered and stored.
    static final int MAX_IMAGE_BASE64_LENGTH = (MAX_IMAGE_BYTES / 3 + 1) * 4 + 64;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = MAX_IMAGE_BASE64_LENGTH, message = "image must be at most 10 MB")
    @Pattern(
            regexp = "^data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\s]+$",
            message = "must be a data:image/...;base64,... URI")
    private String imageBase64;

    @NotBlank
    @Size(max = 255)
    private String imageName;
}
