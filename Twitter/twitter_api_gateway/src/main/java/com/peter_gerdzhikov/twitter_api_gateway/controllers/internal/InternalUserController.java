package com.peter_gerdzhikov.twitter_api_gateway.controllers.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.FollowerIdsResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.internal.InternalUserService;

import lombok.RequiredArgsConstructor;

/**
 * Service-to-service reads for the timeline service. {@code InternalApiSecretFilter} guards the whole prefix, so
 * nothing here checks a caller.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String DEFAULT_PAGE_SIZE = "1000";

    private final InternalUserService internalUserService;

    @GetMapping("/{id}/follower-ids")
    public FollowerIdsResponseDTO getFollowerIds(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return internalUserService.getFollowerIds(id, cursor, size);
    }

    @GetMapping
    public List<InternalUserResponseDTO> getUsers(@RequestParam List<UUID> ids) {
        return internalUserService.getUsers(ids);
    }
}
