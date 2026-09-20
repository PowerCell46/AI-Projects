package com.peter_gerdzhikov.url_shortener_backend.controllers;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.url_shortener_backend.exceptions.ShortUrlNotFoundException;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final ShortUrlService shortUrlService;

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        log.info("Received redirect request for code: {}.", sanitizeForLog(code));
        String originalUrl = shortUrlService
                .resolveOriginalUrl(code)
                .orElseThrow(ShortUrlNotFoundException::new);

        return redirectTo(originalUrl);
    }

    private ResponseEntity<Void> redirectTo(String originalUrl) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    private String sanitizeForLog(String value) {
        return value.replaceAll("\\p{Cntrl}", "_");
    }
}
