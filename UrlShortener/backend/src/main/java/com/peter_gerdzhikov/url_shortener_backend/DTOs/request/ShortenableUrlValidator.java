package com.peter_gerdzhikov.url_shortener_backend.DTOs.request;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import com.peter_gerdzhikov.url_shortener_backend.utilities.InternalHostGuard;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ShortenableUrlValidator implements ConstraintValidator<ShortenableUrl, String> {

    private static final int MAX_URL_BYTES = 1000;

    @Override
    public boolean isValid(String url, ConstraintValidatorContext context) {
        if (url == null || url.isBlank()) {
            return true;
        }

        if (containsControlCharacter(url) || url.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES) {
            return false;
        }

        URI uri;
        try {
            uri = new URI(url);

        } catch (URISyntaxException e) {
            return false;
        }

        return hasAllowedScheme(uri) && hasPublicHost(uri);
    }

    private boolean containsControlCharacter(String url) {
        return url.chars().anyMatch(Character::isISOControl);
    }

    private boolean hasAllowedScheme(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }

    private boolean hasPublicHost(URI uri) {
        String host = uri.getHost();
        return host != null && !InternalHostGuard.isInternal(host);
    }
}
