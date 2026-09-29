package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.files.FileContentResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    /**
     * Private because the route needs a login; immutable because a replaced picture always gets a new id.
     */
    private static final CacheControl CACHE_CONTROL = CacheControl
            .maxAge(Duration.ofDays(365))
            .cachePrivate()
            .immutable();

    private final FileService fileService;

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable UUID id) {
        FileContentResponseDTO file = fileService.open(id);

        return ResponseEntity
                .ok()
                .cacheControl(CACHE_CONTROL)
                .contentLength(file.getSizeBytes())
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(new InputStreamResource(file.getContent()));
    }
}
