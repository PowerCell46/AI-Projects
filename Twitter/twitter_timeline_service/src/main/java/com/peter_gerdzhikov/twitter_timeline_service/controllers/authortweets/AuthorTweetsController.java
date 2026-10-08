package com.peter_gerdzhikov.twitter_timeline_service.controllers.authortweets;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.authortweets.AuthorTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.authortweets.AuthorTweetsService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/author-tweets")
public class AuthorTweetsController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final AuthorTweetsService authorTweetsService;

    @GetMapping("/{authorId}")
    public AuthorTweetsResponseDTO list(
            @CurrentUserId UUID userId,
            @PathVariable UUID authorId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return authorTweetsService.list(userId, authorId, cursor, size);
    }
}
