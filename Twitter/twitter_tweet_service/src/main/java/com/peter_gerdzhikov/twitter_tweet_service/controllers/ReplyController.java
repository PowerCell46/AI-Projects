package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
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

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.request.CreateReplyRequestDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.request.UpdateReplyRequestDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.replies.ReplyService;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tweets/{tweetId}/replies")
public class ReplyController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final ReplyService replyService;

    @PostMapping
    public ResponseEntity<ReplyResponseDTO> create(
            @CurrentUserId UUID userId,
            @PathVariable UUID tweetId,
            @RequestBody CreateReplyRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(replyService.create(userId, tweetId, request.getContent()));
    }

    /**
     * The caller id is unused, but declaring it makes the missing-identity check apply to this route too.
     */
    @GetMapping
    public ReplyPageResponseDTO list(
            @CurrentUserId UUID userId,
            @PathVariable UUID tweetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return replyService.list(tweetId, cursor, size);
    }

    @PutMapping("/{replyId}")
    public ReplyResponseDTO update(
            @CurrentUserId UUID userId,
            @PathVariable UUID tweetId,
            @PathVariable UUID replyId,
            @RequestBody UpdateReplyRequestDTO request
    ) {
        return replyService.update(userId, tweetId, replyId, request.getContent());
    }

    @DeleteMapping("/{replyId}")
    public ResponseEntity<Void> delete(
            @CurrentUserId UUID userId,
            @PathVariable UUID tweetId,
            @PathVariable UUID replyId
    ) {
        replyService.delete(userId, tweetId, replyId);

        return ResponseEntity
                .noContent()
                .build();
    }
}
