package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetSummaryResponseDTO;
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

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final TweetService tweetService;

    @GetMapping
    public List<TweetResponseDTO> findByIds(@RequestParam List<UUID> ids) {
        return tweetService.findByIds(ids);
    }

    @GetMapping("/by-author/{authorId}/page")
    public TweetPageResponseDTO findPageByAuthor(
            @PathVariable UUID authorId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return tweetService.findPageByAuthor(authorId, cursor, size);
    }

    @GetMapping("/by-author/{authorId}")
    public List<TweetSummaryResponseDTO> findNewestByAuthor(
            @PathVariable UUID authorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam int limit
    ) {
        return tweetService.findNewestByAuthor(authorId, since, limit);
    }
}
