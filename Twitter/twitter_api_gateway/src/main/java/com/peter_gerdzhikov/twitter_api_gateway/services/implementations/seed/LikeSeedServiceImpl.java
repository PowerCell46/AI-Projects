package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.LikeSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ConnectRetry;

import lombok.extern.slf4j.Slf4j;

/**
 * Likes through the timeline service with the caller header the gateway would add. Like the tweets, a
 * timeline service that is down only costs the seed its likes.
 */
@Slf4j
@Service
public class LikeSeedServiceImpl implements LikeSeedService {

    private static final long RANDOM_SEED = 2028;

    private static final double LIKE_PROBABILITY = 0.3;

    private static final String CALLER_HEADER = "X-User-Id";

    private final RestClient restClient;

    public LikeSeedServiceImpl(
            @Value("${app.timeline-service.url}") String timelineServiceUrl,
            RestClient.Builder builder
    ) {
        this.restClient = builder
                .baseUrl(timelineServiceUrl)
                .build();
    }

    @Override
    public void seedLikes(List<UUID> userIds, List<SeededTweetClientDTO> tweets) {
        Random random = new Random(RANDOM_SEED);
        int liked = 0;

        try {
            for (UUID userId : userIds) {
                for (SeededTweetClientDTO tweet : tweets) {
                    if (!tweet.getAuthorId().equals(userId) && random.nextDouble() < LIKE_PROBABILITY) {
                        like(userId, tweet.getId());
                        liked++;
                    }
                }
            }

        } catch (RestClientException e) {
            log.warn("The timeline service did not take the seed likes; stopping after {}.", liked, e);
            return;
        }

        log.info("Seeded {} likes.", liked);
    }

    private void like(UUID userId, UUID tweetId) {
        ConnectRetry.runWithConnectRetry(() -> putLike(userId, tweetId), "the timeline service");
    }

    private void putLike(UUID userId, UUID tweetId) {
        restClient
                .put()
                .uri("/api/v1/likes/{tweetId}", tweetId)
                .header(CALLER_HEADER, userId.toString())
                .retrieve()
                .toBodilessEntity();
    }
}
