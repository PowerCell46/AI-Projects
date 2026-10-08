package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.TweetSeedService;

import lombok.extern.slf4j.Slf4j;

/**
 * Posts straight to the tweet service with the caller header the gateway would add, because the seeded
 * users have no session to log in with. A tweet service that is down only costs the seed its tweets: the
 * users stay, so the tweets are not retried on the next start.
 */
@Slf4j
@Service
public class TweetSeedServiceImpl implements TweetSeedService {

    private static final long RANDOM_SEED = 2027;

    private static final int TWEETS_PER_USER = 3;

    private static final String CALLER_HEADER = "X-User-Id";

    private static final List<String> TWEETS = List.of(
            "Good morning, everyone. Coffee first, then the world.",
            "Just shipped something small and it already feels huge.",
            "Hot take: tabs are fine. Spaces are fine. Merge conflicts are not.",
            "Anyone else think the best ideas arrive in the shower?",
            "Reading a good book on the train. Missed my stop. Worth it.",
            "Monday again. We go on.",
            "Rain outside, music on, nothing to fix. Perfect.",
            "Learned something new today and immediately forgot where I put my keys.",
            "If it works on the first try, be suspicious.",
            "Weekend plan: mountain, friends, no laptop.",
            "Debugging is just a detective story where you are also the culprit.",
            "Tried a new recipe. The smoke alarm approved.",
            "Small steps every day beat big plans someday.",
            "Looking for a good podcast recommendation. Go.",
            "The best code review comment is a question, not an order.",
            "Sunsets here never get old.",
            "Cold start, warm tea, long day ahead.",
            "Friday feeling is a real thing and I will defend it.",
            "Wrote a test first today. Feeling unusually responsible.",
            "Why is the last five percent always half of the work?"
    );

    private final RestClient restClient;

    public TweetSeedServiceImpl(@Value("${app.tweet-service.url}") String tweetServiceUrl, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(tweetServiceUrl)
                .build();
    }

    @Override
    public List<SeededTweetClientDTO> seedTweets(List<UUID> authorIds) {
        Random random = new Random(RANDOM_SEED);
        List<SeededTweetClientDTO> created = new ArrayList<>();

        try {
            for (UUID authorId : authorIds) {
                for (int i = 0; i < TWEETS_PER_USER; i++) {
                    created.add(postTweet(authorId, TWEETS.get(random.nextInt(TWEETS.size()))));
                }
            }

        } catch (RestClientException e) {
            log.warn("The tweet service did not take the seed tweets; stopping after {}.", created.size(), e);
            return created;
        }

        log.info("Seeded {} tweets.", created.size());
        return created;
    }

    private SeededTweetClientDTO postTweet(UUID authorId, String content) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("content", content);

        return restClient
                .post()
                .uri("/api/v1/tweets")
                .header(CALLER_HEADER, authorId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(SeededTweetClientDTO.class);
    }
}
