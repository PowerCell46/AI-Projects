package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FollowLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class FollowLookupServiceIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Value("${app.internal-api.secret}")
    private String internalSecret;

    @Autowired
    private FollowLookupService followLookupService;

    private final UUID followerId = TestIds.userId();

    private final UUID followeeId = TestIds.userId();

    @Nested
    class IsFollowing {

        @Test
        void should_return_true_when_the_gateway_answers_200_with_following_true() {
            stubFollowing("{\"following\":true}");

            assertThat(followLookupService.isFollowing(followerId, followeeId)).isTrue();
        }

        @Test
        void should_return_false_when_the_gateway_answers_200_with_following_false() {
            stubFollowing("{\"following\":false}");

            assertThat(followLookupService.isFollowing(followerId, followeeId)).isFalse();
        }

        @Test
        void should_ask_for_that_follower_and_followee_and_send_the_internal_secret() {
            stubFollowing("{\"following\":true}");

            followLookupService.isFollowing(followerId, followeeId);

            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(followCheckPath()))
                    .withHeader("X-Internal-Secret", equalTo(internalSecret)));
        }

        @Test
        void should_make_one_request_and_not_retry_when_the_answer_is_false() {
            stubFollowing("{\"following\":false}");

            followLookupService.isFollowing(followerId, followeeId);

            GATEWAY_STUB.verifyThat(1, anyRequestedFor(anyUrl()));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_throw_unavailable_when_the_gateway_answers_5xx() {
            GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath()))
                    .willReturn(aResponse().withStatus(503).withBody("secret downstream detail")));

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
        }

        @Test
        void should_throw_unavailable_when_the_gateway_answers_404_as_it_does_for_a_wrong_secret() {
            GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath()))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"status\":404,\"messages\":[\"No resource found for this path.\"]}")));

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_gateway_answers_204() {
            stubStatus(204);

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_answer_has_no_following_field() {
            stubFollowing("{}");

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_answer_has_no_body() {
            stubStatus(200);

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_gateway_answers_401() {
            stubStatus(401);

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_connection_is_reset() {
            GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath()))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_gateway_is_slower_than_the_read_timeout() {
            GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath()))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"following\":true}")
                            .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> followLookupService.isFollowing(followerId, followeeId))
                    .isInstanceOf(UpstreamTimeoutException.class)
                    .hasMessage("Upstream service timed out.");
        }
    }

    private String followCheckPath() {
        return "/internal/v1/users/" + followerId + "/follows/" + followeeId;
    }

    private void stubFollowing(String body) {
        GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubStatus(int status) {
        GATEWAY_STUB.register(get(urlPathEqualTo(followCheckPath())).willReturn(aResponse().withStatus(status)));
    }
}
