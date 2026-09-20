package com.peter_gerdzhikov.url_shortener_backend.entities;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The inherited {@code id} is the original URL itself. That is what makes the create path a
 * primary-key lookup, and is why the URL is capped at 1000 bytes: Mongo's {@code _id} index key
 * limit is roughly 1024.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "short_urls")
public class ShortUrl extends CommonEntity {

    @Indexed(unique = true)
    private String code;

    public ShortUrl(String originalUrl, String code) {
        setId(originalUrl);
        this.code = code;
    }
}
