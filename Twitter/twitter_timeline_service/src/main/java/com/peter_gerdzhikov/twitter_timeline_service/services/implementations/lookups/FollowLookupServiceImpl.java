package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.follows.FollowCheckClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream.RestClientConfiguration;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowLookupService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FollowLookupServiceImpl extends DownstreamLookupSupport implements FollowLookupService {

    private static final String FOLLOW_PATH = "/internal/v1/users/{followerId}/follows/{followeeId}";

    private final RestClient gatewayRestClient;

    public FollowLookupServiceImpl(@Qualifier(RestClientConfiguration.GATEWAY_REST_CLIENT) RestClient gatewayRestClient) {
        super("gateway");
        this.gatewayRestClient = gatewayRestClient;
    }

    @Override
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        FollowCheckClientDTO answer = fetchAnswer(followerId, followeeId);

        if (answer == null || answer.getFollowing() == null) {
            log.warn("The gateway answered the follow check without an answer.");
            throw new UpstreamUnavailableException(new IllegalStateException("The follow check has no answer."));
        }

        return answer.getFollowing();
    }

    /**
     * Only a 200 with the answer in its body counts: the gateway also answers 404 for a wrong secret and for a
     * route it does not have, and those must be retried, never read as "not following".
     */
    private FollowCheckClientDTO fetchAnswer(UUID followerId, UUID followeeId) {
        return execute(() -> gatewayRestClient
                .get()
                .uri(FOLLOW_PATH, followerId, followeeId)
                .retrieve()
                .body(FollowCheckClientDTO.class));
    }
}
