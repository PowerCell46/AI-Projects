package com.peter_gerdzhikov.twitter_timeline_service.controllers.tweetdetails;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.tweetdetails.TweetDetailsService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.web.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tweet-details")
public class TweetDetailsController {

    private final TweetDetailsService tweetDetailsService;

    @GetMapping("/{tweetId}")
    public TweetItemResponseDTO get(@CurrentUserId UUID userId, @PathVariable UUID tweetId) {
        return tweetDetailsService.get(userId, tweetId);
    }
}
