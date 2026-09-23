package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.FeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.FeedFilter;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.FeedService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private static final String DEFAULT_FILTER = "ALL";

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final FeedService feedService;

    @GetMapping
    public ResponseEntity<FeedResponseDTO> findFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = DEFAULT_FILTER) FeedFilter filter,
            @Size(max = 100) @RequestParam(required = false) String after,
            @Min(1) @Max(100) @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        log.info("Received feed request with filter {}.", filter);
        FeedResponseDTO feed = feedService.findFeed(UUID.fromString(jwt.getSubject()), filter, after, size);

        return ResponseEntity.ok(feed);
    }
}
