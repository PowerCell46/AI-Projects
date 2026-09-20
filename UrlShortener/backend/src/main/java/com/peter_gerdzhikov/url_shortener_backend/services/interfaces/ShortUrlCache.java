package com.peter_gerdzhikov.url_shortener_backend.services.interfaces;

import java.util.Optional;

public interface ShortUrlCache {

    void put(String code, String originalUrl);

    Optional<String> get(String code);
}
