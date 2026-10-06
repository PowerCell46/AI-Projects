package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.requestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractTimelineServiceIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

/**
 * The timeline service is a WireMock stand-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TimelineRoutesIntegrationTest extends AbstractTimelineServiceIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String FEED_PATH = "/api/v1/feed";

    private static final String SAVED_TWEETS_PATH = "/api/v1/saved-tweets";

    private static final String SAVED_TWEET_PATH = SAVED_TWEETS_PATH + "/6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

    private static final String LIKES_PATH = "/api/v1/likes";

    private static final String LIKE_PATH = LIKES_PATH + "/6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

    private static final String VIEWS_PATH = "/api/v1/views";

    private static final String REPORT_BODY = "{\"tweetIds\":[\"6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b\"]}";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final String DOWNSTREAM_BODY = "{\"items\":[],\"nextCursor\":null}";

    @Autowired
    private RestTestClient restTestClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void resetTheTimelineServiceStandIn() {
        TIMELINE_SERVICE_STUB.resetMappings();
        TIMELINE_SERVICE_STUB.resetRequests();
        stubEveryRequestWith(200, DOWNSTREAM_BODY);
    }

    @Nested
    class Authentication {

        @Test
        void should_return_401_and_forward_nothing_when_there_is_no_cookie() {
            send(FEED_PATH, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }

        @Test
        void should_return_401_and_forward_nothing_when_the_cookie_is_not_a_valid_jwt() {
            send(FEED_PATH, "not-a-jwt")
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }
    }

    @Nested
    class Forwarding {

        @Test
        void should_forward_a_get_with_the_path_query_and_status_unchanged_when_the_user_is_authenticated() {
            String pathAndQuery = FEED_PATH + "?size=5&cursor=abc";

            send(pathAndQuery, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(pathAndQuery)));
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 404, 502, 504})
        void should_pass_the_downstream_error_status_and_body_through_unchanged(int status) {
            String errorBody = "{\"status\":%d,\"messages\":[\"From the timeline service.\"]}".formatted(status);
            stubEveryRequestWith(status, errorBody);

            send(FEED_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isEqualTo(status)
                    .expectBody(String.class).isEqualTo(errorBody);
        }

        @Test
        void should_not_forward_a_path_below_the_feed() {
            send(FEED_PATH + "/anything", cookieFor(UUID.randomUUID()));

            assertNothingWasForwarded();
        }

        @Test
        void should_not_send_the_tweet_routes_to_the_timeline_service() {
            send("/api/v1/tweets/6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b", cookieFor(UUID.randomUUID()));

            assertNothingWasForwarded();
        }
    }

    @Nested
    class Identity {

        @Test
        void should_send_the_jwt_subject_as_x_user_id_when_the_request_is_forwarded() {
            UUID userId = UUID.randomUUID();

            send(FEED_PATH, cookieFor(userId))
                    .expectStatus().isOk();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"X-User-Id", "x-user-id"})
        void should_replace_a_spoofed_x_user_id_with_the_jwt_subject(String headerName) {
            UUID userId = UUID.randomUUID();

            restTestClient.get()
                    .uri(FEED_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(userId))
                    .header(headerName, UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isOk();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @Test
        void should_drop_a_spoofed_x_user_role_header() {
            restTestClient.get()
                    .uri(FEED_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header("X-User-Role", "ADMIN")
                    .exchange()
                    .expectStatus().isOk();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(FEED_PATH))
                    .withoutHeader("X-User-Role"));
        }

        @Test
        void should_not_forward_the_cookie_header() {
            send(FEED_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(FEED_PATH))
                    .withoutHeader(HttpHeaders.COOKIE));
        }

        @Test
        void should_not_forward_the_authorization_header() {
            restTestClient.get()
                    .uri(FEED_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .exchange()
                    .expectStatus().isOk();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(FEED_PATH))
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }

        @Test
        void should_not_send_the_internal_secret_to_the_timeline_service() {
            send(FEED_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(FEED_PATH))
                    .withoutHeader("X-Internal-Secret"));
        }
    }

    @Nested
    class SavedTweets {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "PUT", "DELETE"})
        void should_return_401_and_forward_nothing_when_there_is_no_cookie(String method) {
            send(HttpMethod.valueOf(method), SAVED_TWEET_PATH, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }

        @Test
        void should_forward_a_put_with_the_path_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            send(HttpMethod.PUT, SAVED_TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("PUT", urlEqualTo(SAVED_TWEET_PATH)));
        }

        @Test
        void should_forward_a_delete_with_the_path_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            send(HttpMethod.DELETE, SAVED_TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("DELETE", urlEqualTo(SAVED_TWEET_PATH)));
        }

        @Test
        void should_forward_a_get_of_the_list_with_the_query_and_status_unchanged_when_the_user_is_authenticated() {
            String pathAndQuery = SAVED_TWEETS_PATH + "?size=5&cursor=abc";

            send(HttpMethod.GET, pathAndQuery, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(pathAndQuery)));
        }

        @Test
        void should_pass_the_downstream_404_and_body_through_unchanged_when_the_tweet_is_unknown() {
            String errorBody = "{\"status\":404,\"messages\":[\"Tweet not found.\"]}";
            stubEveryRequestWith(404, errorBody);

            send(HttpMethod.PUT, SAVED_TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNotFound()
                    .expectBody(String.class).isEqualTo(errorBody);
        }

        @Test
        void should_send_the_jwt_subject_as_x_user_id_and_drop_a_spoofed_one_when_a_put_is_forwarded() {
            UUID userId = UUID.randomUUID();
            stubEveryRequestWith(204, "");

            restTestClient.put()
                    .uri(SAVED_TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(userId))
                    .header(USER_ID_HEADER, UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @Test
        void should_not_forward_the_cookie_or_the_authorization_header_when_a_put_is_forwarded() {
            stubEveryRequestWith(204, "");

            restTestClient.put()
                    .uri(SAVED_TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .exchange()
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("PUT", urlEqualTo(SAVED_TWEET_PATH))
                    .withoutHeader(HttpHeaders.COOKIE)
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }
    }

    @Nested
    class Views {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST"})
        void should_return_401_and_forward_nothing_when_there_is_no_cookie(String method) {
            send(HttpMethod.valueOf(method), VIEWS_PATH, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }

        @Test
        void should_forward_a_post_with_its_body_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            postReport(cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("POST", urlEqualTo(VIEWS_PATH))
                    .withRequestBody(equalToJson(REPORT_BODY)));
        }

        @Test
        void should_forward_a_get_with_the_query_and_status_unchanged_when_the_user_is_authenticated() {
            String pathAndQuery = VIEWS_PATH + "?tweetIds=a,b";

            send(HttpMethod.GET, pathAndQuery, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlPathEqualTo(VIEWS_PATH))
                    .withQueryParam("tweetIds", equalTo("a,b")));
        }

        @Test
        void should_pass_the_downstream_400_and_body_through_unchanged_when_the_batch_is_rejected() {
            String errorBody = "{\"status\":400,\"messages\":[\"Between 1 and 50 tweet ids are required, none of them null.\"]}";
            stubEveryRequestWith(400, errorBody);

            postReport(cookieFor(UUID.randomUUID()))
                    .expectStatus().isBadRequest()
                    .expectBody(String.class).isEqualTo(errorBody);
        }

        @Test
        void should_send_the_jwt_subject_as_x_user_id_and_drop_a_spoofed_one_when_a_post_is_forwarded() {
            UUID userId = UUID.randomUUID();
            stubEveryRequestWith(204, "");

            restTestClient.post()
                    .uri(VIEWS_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(userId))
                    .header(USER_ID_HEADER, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(REPORT_BODY)
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @Test
        void should_not_forward_the_cookie_or_the_authorization_header_when_a_post_is_forwarded() {
            stubEveryRequestWith(204, "");

            restTestClient.post()
                    .uri(VIEWS_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(REPORT_BODY)
                    .exchange()
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("POST", urlEqualTo(VIEWS_PATH))
                    .withoutHeader(HttpHeaders.COOKIE)
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }

        @Test
        void should_not_forward_a_path_below_views() {
            send(HttpMethod.GET, VIEWS_PATH + "/anything", cookieFor(UUID.randomUUID()));

            assertNothingWasForwarded();
        }
    }

    @Nested
    class Likes {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "PUT", "DELETE"})
        void should_return_401_and_forward_nothing_when_there_is_no_cookie(String method) {
            send(HttpMethod.valueOf(method), LIKE_PATH, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }

        @Test
        void should_forward_a_put_with_the_path_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            send(HttpMethod.PUT, LIKE_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("PUT", urlEqualTo(LIKE_PATH)));
        }

        @Test
        void should_forward_a_delete_with_the_path_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            send(HttpMethod.DELETE, LIKE_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("DELETE", urlEqualTo(LIKE_PATH)));
        }

        @Test
        void should_forward_a_get_of_the_list_with_the_query_and_status_unchanged_when_the_user_is_authenticated() {
            String pathAndQuery = LIKES_PATH + "?size=5&cursor=abc";

            send(HttpMethod.GET, pathAndQuery, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(pathAndQuery)));
        }

        @Test
        void should_pass_the_downstream_404_and_body_through_unchanged_when_the_tweet_is_unknown() {
            String errorBody = "{\"status\":404,\"messages\":[\"Tweet not found.\"]}";
            stubEveryRequestWith(404, errorBody);

            send(HttpMethod.PUT, LIKE_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNotFound()
                    .expectBody(String.class).isEqualTo(errorBody);
        }

        @Test
        void should_send_the_jwt_subject_as_x_user_id_and_drop_a_spoofed_one_when_a_put_is_forwarded() {
            UUID userId = UUID.randomUUID();
            stubEveryRequestWith(204, "");

            restTestClient.put()
                    .uri(LIKE_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(userId))
                    .header(USER_ID_HEADER, UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isNoContent();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @Test
        void should_not_forward_the_cookie_or_the_authorization_header_when_a_put_is_forwarded() {
            stubEveryRequestWith(204, "");

            restTestClient.put()
                    .uri(LIKE_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .exchange()
                    .expectStatus().isNoContent();

            TIMELINE_SERVICE_STUB.verifyThat(1, requestedFor("PUT", urlEqualTo(LIKE_PATH))
                    .withoutHeader(HttpHeaders.COOKIE)
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_504_without_leaking_exception_text_when_the_timeline_service_is_slower_than_the_read_timeout() {
            TIMELINE_SERVICE_STUB.register(any(anyUrl())
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            ErrorResponseDTO body = send(FEED_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("Upstream service timed out.");
        }
    }

    @Nested
    class UnreachableTimelineService {

        @DynamicPropertySource
        static void pointTheRouteAtAClosedPort(DynamicPropertyRegistry registry) {
            registry.add("app.timeline-service.url", () -> "http://localhost:" + closedPort());
        }

        @Test
        void should_return_502_without_leaking_exception_text_when_the_timeline_service_is_down() {
            ErrorResponseDTO body = send(FEED_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("Upstream service unavailable.");
        }
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RestTestClient.ResponseSpec send(String path, String cookie) {
        return send(HttpMethod.GET, path, cookie);
    }

    private RestTestClient.ResponseSpec send(HttpMethod method, String path, String cookie) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient
                .method(method)
                .uri(path);

        if (cookie != null) {
            request.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        return request.exchange();
    }

    private RestTestClient.ResponseSpec postReport(String cookie) {
        return restTestClient.post()
                .uri(VIEWS_PATH)
                .cookie(CookieFactory.COOKIE_NAME, cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .body(REPORT_BODY)
                .exchange();
    }

    private String cookieFor(UUID userId) {
        return TestJwts.sign(TestJwts.validClaims().subject(userId.toString()), jwtSecret);
    }

    private void stubEveryRequestWith(int status, String body) {
        TIMELINE_SERVICE_STUB.register(any(anyUrl())
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)));
    }

    private void assertNothingWasForwarded() {
        TIMELINE_SERVICE_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
    }

    private List<String> forwardedUserIds() {
        LoggedRequest forwarded = TIMELINE_SERVICE_STUB
                .find(anyRequestedFor(anyUrl()))
                .getFirst();

        return forwarded
                .getHeaders()
                .getHeader(USER_ID_HEADER)
                .values();
    }
}
