package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.CreateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.UpdateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.InterestTopicResponseMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/interest-topics")
@RequiredArgsConstructor
public class InterestTopicController {

    private final InterestTopicService interestTopicService;

    @PostMapping
    public ResponseEntity<InterestTopicResponseDTO> createInterestTopic(
            @Valid @RequestBody CreateInterestTopicRequestDTO request
    ) {
        log.info("Received create interest topic request.");
        InterestTopic interestTopic = interestTopicService.create(
                request.getName(),
                request.getDescription(),
                request.getPrompt(),
                request.getCategoryId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(InterestTopicResponseMapper.toResponse(interestTopic));
    }

    @GetMapping
    public ResponseEntity<Page<InterestTopicResponseDTO>> listInterestTopics(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        // log.info("Received list interest topics request.");
        Page<InterestTopicResponseDTO> page = interestTopicService
                .findPage(categoryId, pageable)
                .map(InterestTopicResponseMapper::toResponse);

        return ResponseEntity.ok(page);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InterestTopicResponseDTO> updateInterestTopic(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInterestTopicRequestDTO request
    ) {
        log.info("Received update interest topic request.");
        InterestTopic interestTopic = interestTopicService.update(
                id,
                request.getName(),
                request.getDescription(),
                request.getPrompt(),
                request.getCategoryId()
        );

        return ResponseEntity.ok(InterestTopicResponseMapper.toResponse(interestTopic));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInterestTopic(@PathVariable UUID id) {
        log.info("Received delete interest topic request.");
        interestTopicService.delete(id);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
