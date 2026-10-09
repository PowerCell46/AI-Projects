package com.peter_gerdzhikov.twitter_timeline_service.controllers.savedtweets;

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

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.savedtweets.SavedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.savedtweets.SavedTweetService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.web.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saved-tweets")
public class SavedTweetController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final SavedTweetService savedTweetService;

    @PutMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@CurrentUserId UUID userId, @PathVariable UUID tweetId) {
        savedTweetService.save(userId, tweetId);
    }

    @DeleteMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@CurrentUserId UUID userId, @PathVariable UUID tweetId) {
        savedTweetService.unsave(userId, tweetId);
    }

    @GetMapping
    public SavedTweetsResponseDTO getSavedTweets(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return savedTweetService.getSavedTweets(userId, cursor, size);
    }
}
