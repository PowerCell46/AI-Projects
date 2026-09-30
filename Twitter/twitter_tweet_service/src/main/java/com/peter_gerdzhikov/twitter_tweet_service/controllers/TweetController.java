package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.request.UpdateTweetRequestDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetImageContentResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.TweetService;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tweets")
public class TweetController {

    /**
     * Private because every route needs the caller header; immutable because a tweet's images never change.
     */
    private static final CacheControl IMAGE_CACHE_CONTROL = CacheControl
            .maxAge(Duration.ofDays(365))
            .cachePrivate()
            .immutable();

    private final TweetService tweetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TweetResponseDTO> create(
            @CurrentUserId UUID userId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tweetService.create(userId, content, images));
    }

    /**
     * The caller id is unused, but declaring it makes the missing-identity check apply to this route too.
     */
    @GetMapping("/{id}")
    public TweetResponseDTO get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return tweetService.get(id);
    }

    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<InputStreamResource> getImage(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID imageId
    ) {
        TweetImageContentResponseDTO image = tweetService.openImage(id, imageId);

        return ResponseEntity
                .ok()
                .cacheControl(IMAGE_CACHE_CONTROL)
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(image.getSizeBytes())
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(new InputStreamResource(image.getContent()));
    }

    @PutMapping("/{id}")
    public TweetResponseDTO update(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestBody UpdateTweetRequestDTO request
    ) {
        return tweetService.update(userId, id, request.getContent());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        tweetService.delete(userId, id);

        return ResponseEntity
                .noContent()
                .build();
    }
}
