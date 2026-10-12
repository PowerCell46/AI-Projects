package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;

class ReplySeedServiceImplTest {

    private static final String TWEET_SERVICE_URL = "http://tweet-service";

    private MockRestServiceServer mockServer;

    private ReplySeedServiceImpl seedService;

    private UUID authorId;

    private UUID tweetId;

    private List<UUID> userIds;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        seedService = new ReplySeedServiceImpl(TWEET_SERVICE_URL, builder);
        authorId = UUID.randomUUID();
        tweetId = UUID.randomUUID();
        userIds = List.of(authorId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void should_post_replies_from_users_other_than_the_author() {
        // Fixed by the seed: the first tweet draws repliers userIds[2], userIds[1], userIds[4].
        for (UUID replierId : List.of(userIds.get(2), userIds.get(1), userIds.get(4))) {
            mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets/" + tweetId + "/replies"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-User-Id", replierId.toString()))
                    .andExpect(content().contentType("application/json"))
                    .andRespond(withSuccess());
        }

        seedService.seedReplies(userIds, List.of(tweet(authorId)));

        mockServer.verify();
    }

    @Test
    void should_stop_leaving_later_replies_unposted_when_the_tweet_service_errors() {
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets/" + tweetId + "/replies"))
                .andRespond(withSuccess());
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets/" + tweetId + "/replies"))
                .andRespond(withServerError());

        seedService.seedReplies(userIds, List.of(tweet(authorId)));

        mockServer.verify();
    }

    @Test
    void should_retry_a_refused_connection_until_the_tweet_service_answers() {
        mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets/" + tweetId + "/replies"))
                .andRespond(request -> {
                    throw new ResourceAccessException("refused");
                });
        for (int i = 0; i < 3; i++) {
            mockServer.expect(requestTo(TWEET_SERVICE_URL + "/api/v1/tweets/" + tweetId + "/replies"))
                    .andRespond(withSuccess());
        }

        seedService.seedReplies(userIds, List.of(tweet(authorId)));

        mockServer.verify();
    }

    private SeededTweetClientDTO tweet(UUID authorId) {
        SeededTweetClientDTO tweet = new SeededTweetClientDTO();
        tweet.setId(tweetId);
        tweet.setAuthorId(authorId);

        return tweet;
    }
}
