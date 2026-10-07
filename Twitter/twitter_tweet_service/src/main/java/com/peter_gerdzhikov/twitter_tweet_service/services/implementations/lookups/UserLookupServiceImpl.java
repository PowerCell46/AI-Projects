package com.peter_gerdzhikov.twitter_tweet_service.services.implementations.lookups;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.configurations.downstream.RestClientConfiguration;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.lookups.UserLookupService;

@Service
public class UserLookupServiceImpl extends DownstreamLookupSupport implements UserLookupService {

    private static final String USERS_PATH = "/internal/v1/users?ids={ids}";

    private final RestClient gatewayRestClient;

    public UserLookupServiceImpl(@Qualifier(RestClientConfiguration.GATEWAY_REST_CLIENT) RestClient gatewayRestClient) {
        super("gateway");
        this.gatewayRestClient = gatewayRestClient;
    }

    @Override
    public Map<UUID, UserClientDTO> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<UserClientDTO> users = execute(() -> gatewayRestClient
                .get()
                .uri(USERS_PATH, commaSeparated(ids))
                .retrieve()
                .body(new ParameterizedTypeReference<List<UserClientDTO>>() {
                }));

        return users
                .stream()
                .collect(Collectors.toMap(UserClientDTO::getId, Function.identity(), (first, second) -> first));
    }
}
