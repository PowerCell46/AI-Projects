package com.peter_gerdzhikov.twitter_timeline_service.controllers.feed;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.feed.FeedResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.web.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feed")
public class FeedController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final FeedService feedService;

    @GetMapping
    public FeedResponseDTO getFeed(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return feedService.getFeed(userId, cursor, size);
    }
}
