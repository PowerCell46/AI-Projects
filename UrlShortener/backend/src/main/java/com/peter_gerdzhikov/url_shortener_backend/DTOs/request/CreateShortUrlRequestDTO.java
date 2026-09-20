package com.peter_gerdzhikov.url_shortener_backend.DTOs.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateShortUrlRequestDTO {

    @NotBlank
    @ShortenableUrl
    private String url;

    public CreateShortUrlRequestDTO(String url) {
        setUrl(url);
    }

    public void setUrl(String url) {
        this.url = url == null ? null : url.trim();
    }
}
