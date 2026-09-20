package com.peter_gerdzhikov.url_shortener_backend.services.interfaces;

import java.util.Optional;

import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;

public interface ShortUrlService {

    ShortUrl createOrGetShortUrl(String originalUrl);

    Optional<String> resolveOriginalUrl(String code);
}
