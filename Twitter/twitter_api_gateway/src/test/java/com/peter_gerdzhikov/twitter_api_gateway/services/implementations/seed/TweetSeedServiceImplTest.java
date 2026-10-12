package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;

class TweetSeedServiceImplTest {

    private static final String TWEET_SERVICE_URL = "http://tweet-service";

    private MockRestServiceServer mockServer;

    private TweetSeedServiceImpl seedService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        seedService = new TweetSeedServiceImpl(TWEET_SERVICE_URL, builder);
    }

    @Test
    void should_post_three_tweets_per_author_with_the_caller_header() {
        UUID authorId = UUID.randomUUID();
        List<UUID> tweetIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID tweetId = UUID.randomUUID();
            tweetIds.add(tweetId);
            mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", authorId.toString()))
                    .andRespond(withSuccess(tweetJson(tweetId, authorId), MediaType.APPLICATION_JSON));
        }

        List<SeededTweetClientDTO> created = seedService.seedTweets(List.of(authorId));

        assertThat(created).map(SeededTweetClientDTO::getId).containsExactlyElementsOf(tweetIds);
        mockServer.verify();
    }

    @Test
    void should_keep_the_tweets_posted_before_a_server_error() {
        UUID authorId = UUID.randomUUID();
        UUID firstTweetId = UUID.randomUUID();
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets"))
                .andRespond(withSuccess(tweetJson(firstTweetId, authorId), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets"))
                .andRespond(withServerError());

        List<SeededTweetClientDTO> created = seedService.seedTweets(List.of(authorId));

        assertThat(created).map(SeededTweetClientDTO::getId).containsExactly(firstTweetId);
        mockServer.verify();
    }

    @Test
    void should_retry_a_refused_connection_until_the_tweet_service_answers() {
        UUID authorId = UUID.randomUUID();
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets"))
                .andRespond(request -> {
                    throw new ResourceAccessException("refused");
                });
        List<UUID> tweetIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID tweetId = UUID.randomUUID();
            tweetIds.add(tweetId);
            mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets"))
                    .andRespond(withSuccess(tweetJson(tweetId, authorId), MediaType.APPLICATION_JSON));
        }

        List<SeededTweetClientDTO> created = seedService.seedTweets(List.of(authorId));

        assertThat(created).map(SeededTweetClientDTO::getId).containsExactlyElementsOf(tweetIds);
        mockServer.verify();
    }

    private String tweetJson(UUID tweetId, UUID authorId) {
        return "{\"id\":\"" + tweetId + "\",\"authorId\":\"" + authorId + "\"}";
    }
}
