package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.services.interfaces.AssetService;
import com.wealthbuilder.backend.utils.DataUriImage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;


/**
 * Asset catalog endpoints. Reads are open to any authenticated user; writes are restricted to
 * moderators via {@code @PreAuthorize}. List and detail omit the image blob — clients fetch it
 * lazily from {@link #image(Long)} so list payloads stay small.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public List<AssetResponse> list() {
        return assetService.findAll();
    }

    @GetMapping("/{id}")
    public AssetResponse detail(@PathVariable Long id) {
        return assetService.findById(id);
    }

    @GetMapping("/by-slug/{slug}")
    public AssetResponse detailBySlug(@PathVariable String slug) {
        return assetService.findBySlug(slug);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        final DataUriImage image = assetService.findImage(id);

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(image.getMediaType())
                .contentLength(image.getBytes().length)
                .body(image.getBytes());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MODERATOR')")
    public AssetResponse create(@Valid @RequestBody AssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MODERATOR')")
    public void delete(@PathVariable Long id) {
        assetService.delete(id);
    }
}
