package com.peter_gerdzhikov.url_shortener_backend.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.url_shortener_backend.DTOs.request.CreateShortUrlRequestDTO;
import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ShortUrlResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public ShortUrlController(ShortUrlService shortUrlService, @Value("${url-shortener.base-url}") String baseUrl) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = baseUrl;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponseDTO> createShortUrl(@Valid @RequestBody CreateShortUrlRequestDTO request) {
        log.info("Received request to create short URL.");
        ShortUrl shortUrl = shortUrlService.createOrGetShortUrl(request.getUrl());
        return ResponseEntity.ok(new ShortUrlResponseDTO(baseUrl + "/" + shortUrl.getCode()));
    }
}
