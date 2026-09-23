package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.ExistingInterestTopicsRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ExistingInterestTopicsResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.InterestTopicResponseMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service-to-service surface for the gateway. It sits outside {@code /api/v1}, so the gateway's routes
 * never forward a client to it.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/interest-topics")
public class InternalInterestTopicController {

    private final InterestTopicService interestTopicService;

    @PostMapping("/existing")
    public ResponseEntity<ExistingInterestTopicsResponseDTO> findExistingInterestTopics(
            @Valid @RequestBody ExistingInterestTopicsRequestDTO request
    ) {
        log.info("Received existing interest topics request for {} ids.", request.getIds().size());
        List<UUID> existingIds = interestTopicService.findExistingIds(request.getIds());

        return ResponseEntity.ok(new ExistingInterestTopicsResponseDTO(existingIds));
    }

    @PostMapping("/feed")
    public ResponseEntity<InterestTopicFeedResponseDTO> findInterestTopicFeed(
            @Valid @RequestBody InterestTopicFeedRequestDTO request
    ) {
        log.info("Received interest topic feed request in {} mode for {} ids.", request.getMode(), request.getIds().size());
        Slice<InterestTopic> slice = interestTopicService.findFeedSlice(
                request.getIds(),
                request.getMode(),
                request.getAfter(),
                request.getSize()
        );
        long total = interestTopicService.countAll();
        long matching = interestTopicService.findExistingIds(request.getIds()).size();

        return ResponseEntity.ok(new InterestTopicFeedResponseDTO(toItems(slice), nextCursor(slice), total, matching));
    }

    private List<InterestTopicResponseDTO> toItems(Slice<InterestTopic> slice) {
        return slice
                .map(InterestTopicResponseMapper::toResponse)
                .getContent();
    }

    private String nextCursor(Slice<InterestTopic> slice) {
        if (!slice.hasNext()) {
            return null;
        }

        return slice
                .getContent()
                .getLast()
                .getName();
    }
}
