package com.peter_gerdzhikov.url_shortener_backend.DTOs.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrlResponseDTO {

    private String shortUrl;
}
