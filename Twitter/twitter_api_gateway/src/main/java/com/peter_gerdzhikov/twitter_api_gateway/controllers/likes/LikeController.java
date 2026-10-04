package com.peter_gerdzhikov.twitter_api_gateway.controllers.likes;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A stand-in until a likes service exists: it answers 204 and stores nothing, so the frontend can build the
 * like button against the final routes.
 */
@RestController
@RequestMapping("/api/v1/likes")
public class LikeController {

    @PutMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable UUID tweetId) {
    }

    @DeleteMapping("/{tweetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable UUID tweetId) {
    }
}
