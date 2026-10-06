package com.peter_gerdzhikov.twitter_timeline_service.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.likes.LikedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes.LikeService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/likes")
public class LikeController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final LikeService likeService;

    @PutMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@CurrentUserId UUID userId, @PathVariable UUID tweetId) {
        likeService.like(userId, tweetId);
    }

    @DeleteMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@CurrentUserId UUID userId, @PathVariable UUID tweetId) {
        likeService.unlike(userId, tweetId);
    }

    @GetMapping
    public LikedTweetsResponseDTO getLikedTweets(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return likeService.getLikedTweets(userId, cursor, size);
    }
}
