package com.peter_gerdzhikov.twitter_timeline_service.controllers.views;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.request.ReportViewsRequestDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/views")
public class ViewController {

    private final ViewService viewService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@CurrentUserId UUID userId, @RequestBody ReportViewsRequestDTO request) {
        viewService.report(userId, request.getTweetIds());
    }

    /**
     * The counts are the same for every caller; resolving the caller only enforces the header like every other
     * endpoint.
     */
    @GetMapping
    public Map<UUID, Long> getViews(@CurrentUserId UUID userId, @RequestParam List<UUID> tweetIds) {
        return viewService.getViews(tweetIds);
    }
}
