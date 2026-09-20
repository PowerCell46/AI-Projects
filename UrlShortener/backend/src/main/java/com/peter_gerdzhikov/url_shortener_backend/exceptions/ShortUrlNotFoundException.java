package com.peter_gerdzhikov.url_shortener_backend.exceptions;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException() {
        super("No short URL found for the given code.");
    }
}
