package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FollowListService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.FollowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final FollowService followService;

    private final FollowListService followListService;

    @PutMapping("/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        followService.follow(UUID.fromString(jwt.getSubject()), username);
    }

    @DeleteMapping("/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        followService.unfollow(UUID.fromString(jwt.getSubject()), username);
    }

    @GetMapping("/{username}/followers")
    public FollowListResponseDTO getFollowers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return followListService.getFollowers(UUID.fromString(jwt.getSubject()), username, cursor, size);
    }

    @GetMapping("/{username}/following")
    public FollowListResponseDTO getFollowing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return followListService.getFollowing(UUID.fromString(jwt.getSubject()), username, cursor, size);
    }
}
