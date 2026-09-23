package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.SubscribeRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.subscriptions.SubscriptionResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<SubscriptionResponseDTO>> listSubscriptions(@AuthenticationPrincipal Jwt jwt) {
        // log.info("Received list subscriptions request.");
        List<SubscriptionResponseDTO> subscriptions = subscriptionService
                .findAllForUser(callerId(jwt))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(subscriptions);
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponseDTO> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubscribeRequestDTO request
    ) {
        log.info("Received subscribe request.");
        Subscription subscription = subscriptionService.subscribe(callerId(jwt), request.getInterestTopicId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(subscription));
    }

    @DeleteMapping("/{interestTopicId}")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID interestTopicId) {
        log.info("Received unsubscribe request.");
        subscriptionService.unsubscribe(callerId(jwt), interestTopicId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    /**
     * The caller's identity comes from the token alone - never from the body or the path, so one user
     * can never act on another's subscriptions.
     */
    private UUID callerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private SubscriptionResponseDTO toResponse(Subscription subscription) {
        return new SubscriptionResponseDTO(
                subscription.getId(),
                subscription.getInterestTopicId(),
                subscription.getCreatedAt()
        );
    }
}
