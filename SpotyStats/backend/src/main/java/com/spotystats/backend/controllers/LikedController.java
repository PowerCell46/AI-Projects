package com.spotystats.backend.controllers;

import com.spotystats.backend.dtos.listening.LikedPageResponse;
import com.spotystats.backend.services.interfaces.LikedTracksService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class LikedController {

    private static final int MAX_PAGE_SIZE = 50;

    private final LikedTracksService likedTracksService;

    @GetMapping("/api/liked")
    public LikedPageResponse likedPage(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        return likedTracksService.likedPage(clampPageSize(limit), Math.max(offset, 0));
    }

    private static int clampPageSize(int limit) {
        return Math.clamp(limit, 1, MAX_PAGE_SIZE);
    }
}
