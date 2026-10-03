package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.TweetService;

import lombok.RequiredArgsConstructor;

/**
 * Service-to-service reads. The gateway forwards only {@code /api/v1/tweets/**}, so this is not reachable from
 * outside, and it needs no caller identity.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/tweets")
public class InternalTweetController {

    private final TweetService tweetService;

    @GetMapping
    public List<TweetResponseDTO> findByIds(@RequestParam List<UUID> ids) {
        return tweetService.findByIds(ids);
    }
}
