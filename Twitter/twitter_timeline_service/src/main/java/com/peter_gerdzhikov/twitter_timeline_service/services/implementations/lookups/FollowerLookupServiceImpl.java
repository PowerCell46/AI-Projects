package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.FollowerIdsClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream.RestClientConfiguration;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowerLookupService;

@Service
public class FollowerLookupServiceImpl extends DownstreamLookupSupport implements FollowerLookupService {

    private static final int FOLLOWER_PAGE_SIZE = 1000;

    private static final String FIRST_PAGE_PATH = "/internal/v1/users/{id}/follower-ids?size={size}";

    private static final String NEXT_PAGE_PATH = FIRST_PAGE_PATH + "&cursor={cursor}";

    private final RestClient gatewayRestClient;

    public FollowerLookupServiceImpl(@Qualifier(RestClientConfiguration.GATEWAY_REST_CLIENT) RestClient gatewayRestClient) {
        super("gateway");
        this.gatewayRestClient = gatewayRestClient;
    }

    @Override
    public void forEachFollowerPage(UUID userId, Consumer<List<UUID>> pageConsumer) {
        String cursor = null;
        do {
            FollowerIdsClientDTO page = fetchPage(userId, cursor);
            if (!page.getIds().isEmpty()) {
                pageConsumer.accept(page.getIds());
            }

            cursor = page.getNextCursor();
        } while (cursor != null);
    }

    private FollowerIdsClientDTO fetchPage(UUID userId, String cursor) {
        return execute(() -> {
            RestClient.RequestHeadersSpec<?> request = cursor == null
                    ? gatewayRestClient.get().uri(FIRST_PAGE_PATH, userId, FOLLOWER_PAGE_SIZE)
                    : gatewayRestClient.get().uri(NEXT_PAGE_PATH, userId, FOLLOWER_PAGE_SIZE, cursor);

            return request
                    .retrieve()
                    .body(FollowerIdsClientDTO.class);
        });
    }
}
