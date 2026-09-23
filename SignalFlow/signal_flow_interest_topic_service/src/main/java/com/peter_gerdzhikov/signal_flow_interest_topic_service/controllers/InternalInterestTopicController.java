package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.ExistingInterestTopicsRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ExistingInterestTopicsResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;

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
}
