package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ReplySeedService;

import lombok.extern.slf4j.Slf4j;

/**
 * Posts straight to the tweet service with the caller header the gateway would add. A tweet service that
 * stops answering only costs the seed the rest of its replies.
 */
@Slf4j
@Service
public class ReplySeedServiceImpl implements ReplySeedService {

    private static final long RANDOM_SEED = 2029;

    private static final int MAX_REPLIES_PER_TWEET = 3;

    private static final String CALLER_HEADER = "X-User-Id";

    private static final List<String> REPLIES = List.of(
            "Couldn't agree more.",
            "Ha, this made my day.",
            "Same here. Every single time.",
            "Hard disagree, but I respect the take.",
            "Send me the details, I'm curious.",
            "This is the way.",
            "Wait, really? Tell me more.",
            "Saving this for later.",
            "Haha, relatable.",
            "Good luck with that, you've got this.",
            "I tried it last week. Worth it.",
            "Who else is nodding along right now?",
            "That's exactly what I needed to read today.",
            "Honestly, same.",
            "Counterpoint: sleep.",
            "Let's talk about it over coffee."
    );

    private final RestClient restClient;

    public ReplySeedServiceImpl(@Value("${app.tweet-service.url}") String tweetServiceUrl, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(tweetServiceUrl)
                .build();
    }

    @Override
    public void seedReplies(List<UUID> userIds, List<SeededTweetClientDTO> tweets) {
        Random random = new Random(RANDOM_SEED);
        int created = 0;

        try {
            for (SeededTweetClientDTO tweet : tweets) {
                for (UUID replierId : pickRepliers(random, userIds, tweet.getAuthorId())) {
                    postReply(replierId, tweet.getId(), REPLIES.get(random.nextInt(REPLIES.size())));
                    created++;
                }
            }

        } catch (RestClientException e) {
            log.warn("The tweet service did not take the seed replies; stopping after {}.", created, e);
            return;
        }

        log.info("Seeded {} replies.", created);
    }

    private List<UUID> pickRepliers(Random random, List<UUID> userIds, UUID authorId) {
        List<UUID> candidates = new ArrayList<>(userIds);
        candidates.remove(authorId);
        Collections.shuffle(candidates, random);

        return candidates.subList(0, random.nextInt(MAX_REPLIES_PER_TWEET + 1));
    }

    private void postReply(UUID replierId, UUID tweetId, String content) {
        restClient
                .post()
                .uri("/api/v1/tweets/{tweetId}/replies", tweetId)
                .header(CALLER_HEADER, replierId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content))
                .retrieve()
                .toBodilessEntity();
    }
}
