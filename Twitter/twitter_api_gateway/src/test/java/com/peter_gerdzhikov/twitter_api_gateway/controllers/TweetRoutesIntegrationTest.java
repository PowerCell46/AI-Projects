package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.requestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractTweetServiceIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

/**
 * The tweet service is a WireMock stand-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TweetRoutesIntegrationTest extends AbstractTweetServiceIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String TWEET_PATH = "/api/v1/tweets/6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

    private static final String TWEETS_PATH = "/api/v1/tweets";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final String JSON_BODY = "{\"content\":\"hello\"}";

    private static final String DOWNSTREAM_BODY = "{\"forwarded\":true}";

    @Autowired
    private RestTestClient restTestClient;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void resetTheTweetServiceStandIn() {
        TWEET_SERVICE_STUB.resetMappings();
        TWEET_SERVICE_STUB.resetRequests();
        stubEveryRequestWith(200, DOWNSTREAM_BODY);
    }

    @Nested
    class Authentication {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
        void should_return_401_and_forward_nothing_when_there_is_no_cookie(String method) {
            send(HttpMethod.valueOf(method), TWEET_PATH, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }
    }

    @Nested
    class Forwarding {

        @Test
        void should_forward_a_get_with_the_path_query_and_status_unchanged_when_the_user_is_authenticated() {
            String pathAndQuery = TWEET_PATH + "?verbose=true&lang=en";

            send(HttpMethod.GET, pathAndQuery, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(pathAndQuery)));
        }

        @Test
        void should_forward_a_post_with_the_body_and_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(201, DOWNSTREAM_BODY);

            send(HttpMethod.POST, TWEETS_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isCreated()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("POST", urlEqualTo(TWEETS_PATH))
                    .withRequestBody(equalToJson(JSON_BODY)));
        }

        @Test
        void should_forward_a_put_with_the_body_and_status_unchanged_when_the_user_is_authenticated() {
            send(HttpMethod.PUT, TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("PUT", urlEqualTo(TWEET_PATH))
                    .withRequestBody(equalToJson(JSON_BODY)));
        }

        @Test
        void should_forward_a_delete_with_the_status_unchanged_when_the_user_is_authenticated() {
            stubEveryRequestWith(204, "");

            send(HttpMethod.DELETE, TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isNoContent();

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("DELETE", urlEqualTo(TWEET_PATH)));
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 403, 404, 413, 415})
        void should_pass_the_downstream_error_status_and_body_through_unchanged(int status) {
            String errorBody = "{\"status\":%d,\"messages\":[\"From the tweet service.\"]}".formatted(status);
            stubEveryRequestWith(status, errorBody);

            send(HttpMethod.GET, TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isEqualTo(status)
                    .expectBody(String.class).isEqualTo(errorBody);
        }
    }

    @Nested
    class Identity {

        @Test
        void should_send_the_jwt_subject_as_x_user_id_when_the_request_is_forwarded() {
            UUID userId = UUID.randomUUID();

            send(HttpMethod.GET, TWEET_PATH, cookieFor(userId))
                    .expectStatus().isOk();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"X-User-Id", "x-user-id"})
        void should_replace_a_spoofed_x_user_id_with_the_jwt_subject(String headerName) {
            UUID userId = UUID.randomUUID();

            restTestClient.get()
                    .uri(TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(userId))
                    .header(headerName, UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isOk();

            assertThat(forwardedUserIds()).containsExactly(userId.toString());
        }

        @Test
        void should_drop_a_spoofed_x_user_role_header() {
            restTestClient.get()
                    .uri(TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header("X-User-Role", "ADMIN")
                    .exchange()
                    .expectStatus().isOk();

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(TWEET_PATH))
                    .withoutHeader("X-User-Role"));
        }

        @Test
        void should_not_forward_the_cookie_header() {
            send(HttpMethod.GET, TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isOk();

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(TWEET_PATH))
                    .withoutHeader(HttpHeaders.COOKIE));
        }

        @Test
        void should_not_forward_the_authorization_header() {
            restTestClient.get()
                    .uri(TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(UUID.randomUUID()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .exchange()
                    .expectStatus().isOk();

            TWEET_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(TWEET_PATH))
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_504_without_leaking_exception_text_when_the_tweet_service_is_slower_than_the_read_timeout() {
            TWEET_SERVICE_STUB.register(any(anyUrl())
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            ErrorResponseDTO body = send(HttpMethod.GET, TWEET_PATH, cookieFor(UUID.randomUUID()))
                    .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("Upstream service timed out.");
        }
    }

    @Nested
    class UnreachableTweetService {

        @DynamicPropertySource
        static void pointTheRoutesAtAClosedPort(DynamicPropertyRegistry registry) {
            registry.add("app.tweet-service.url", () -> "http://localhost:" + closedPort());
        }

        @Test
        void should_return_502_without_leaking_exception_text_when_the_tweet_service_is_down() {
            ErrorResponseDTO body = send(HttpMethod.GET, TWEET_PATH, cookieFor(UUID.randomUUID()))
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

    private RestTestClient.ResponseSpec send(HttpMethod method, String path, String cookie) {
        RestTestClient.RequestBodySpec request = restTestClient
                .method(method)
                .uri(path);

        if (cookie != null) {
            request.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .body(JSON_BODY);
        }

        return request.exchange();
    }

    private String cookieFor(UUID userId) {
        return TestJwts.sign(TestJwts.validClaims().subject(userId.toString()), jwtSecret);
    }

    private void stubEveryRequestWith(int status, String body) {
        TWEET_SERVICE_STUB.register(any(anyUrl())
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)));
    }

    private void assertNothingWasForwarded() {
        TWEET_SERVICE_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
    }

    private List<String> forwardedUserIds() {
        LoggedRequest forwarded = TWEET_SERVICE_STUB
                .find(anyRequestedFor(anyUrl()))
                .getFirst();

        return forwarded
                .getHeaders()
                .getHeader(USER_ID_HEADER)
                .values();
    }
}
