package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

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

class LikeSeedServiceImplTest {

    private static final String TIMELINE_SERVICE_URL = "http://timeline-service";

    private MockRestServiceServer mockServer;

    private LikeSeedServiceImpl seedService;

    private List<UUID> userIds;

    private List<SeededTweetClientDTO> tweets;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        seedService = new LikeSeedServiceImpl(TIMELINE_SERVICE_URL, builder);
        userIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        tweets = List.of(tweet(0), tweet(1), tweet(2));
    }

    @Test
    void should_like_other_users_tweets_but_never_the_liker_own() {
        // Fixed by the seed: userIds[1] likes tweets[0] and tweets[2], userIds[2] likes tweets[0] and tweets[1].
        expectLike(userIds.get(1), tweets.get(0).getId());
        expectLike(userIds.get(1), tweets.get(2).getId());
        expectLike(userIds.get(2), tweets.get(0).getId());
        expectLike(userIds.get(2), tweets.get(1).getId());

        seedService.seedLikes(userIds, tweets);

        mockServer.verify();
    }

    @Test
    void should_send_no_request_when_every_tweet_is_the_liker_own() {
        seedService.seedLikes(List.of(userIds.get(0)), List.of(tweets.get(0)));

        mockServer.verify();
    }

    @Test
    void should_stop_leaving_later_likes_unsent_when_the_timeline_service_errors() {
        mockServer.expect(requestTo(TIMELINE_SERVICE_URL + "/api/v1/likes/" + tweets.get(0).getId()))
                .andRespond(withSuccess());
        mockServer.expect(requestTo(TIMELINE_SERVICE_URL + "/api/v1/likes/" + tweets.get(2).getId()))
                .andRespond(withServerError());

        seedService.seedLikes(userIds, tweets);

        mockServer.verify();
    }

    @Test
    void should_retry_a_refused_connection_until_the_timeline_service_answers() {
        mockServer.expect(requestTo(TIMELINE_SERVICE_URL + "/api/v1/likes/" + tweets.get(0).getId()))
                .andRespond(request -> {
                    throw new ResourceAccessException("refused");
                });
        expectLike(userIds.get(1), tweets.get(0).getId());
        expectLike(userIds.get(1), tweets.get(2).getId());
        expectLike(userIds.get(2), tweets.get(0).getId());
        expectLike(userIds.get(2), tweets.get(1).getId());

        seedService.seedLikes(userIds, tweets);

        mockServer.verify();
    }

    private void expectLike(UUID userId, UUID tweetId) {
        mockServer.expect(requestTo(TIMELINE_SERVICE_URL + "/api/v1/likes/" + tweetId))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess());
    }

    private SeededTweetClientDTO tweet(int authorIndex) {
        SeededTweetClientDTO tweet = new SeededTweetClientDTO();
        tweet.setId(UUID.randomUUID());
        tweet.setAuthorId(userIds.get(authorIndex));

        return tweet;
    }
}
