package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

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

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream.RestClientConfiguration;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetLookupService;

@Service
public class TweetLookupServiceImpl extends DownstreamLookupSupport implements TweetLookupService {

    private static final String TWEETS_PATH = "/internal/v1/tweets?ids={ids}";

    private final RestClient tweetServiceRestClient;

    public TweetLookupServiceImpl(
            @Qualifier(RestClientConfiguration.TWEET_SERVICE_REST_CLIENT) RestClient tweetServiceRestClient
    ) {
        super("tweet service");
        this.tweetServiceRestClient = tweetServiceRestClient;
    }

    @Override
    public Map<UUID, TweetClientDTO> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<TweetClientDTO> tweets = execute(() -> tweetServiceRestClient
                .get()
                .uri(TWEETS_PATH, commaSeparated(ids))
                .retrieve()
                .body(new ParameterizedTypeReference<List<TweetClientDTO>>() {
                }));

        return tweets
                .stream()
                .collect(Collectors.toMap(TweetClientDTO::getId, Function.identity(), (first, second) -> first));
    }
}
