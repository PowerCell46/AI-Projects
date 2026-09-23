package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractInterestTopicServiceIntegrationTest;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class InterestTopicRoutesIntegrationTest extends AbstractInterestTopicServiceIntegrationTest {

    private static final String CATEGORY_ID = "0b5c3a4e-6f0e-4b8e-9d3a-2c1f7e8a9b01";
    private static final String INTEREST_TOPIC_ID = "7d2e9f10-3c4b-4a5d-8e6f-1a2b3c4d5e6f";
    private static final String DOWNSTREAM_BODY = "{\"forwarded\":true}";
    private static final String REQUEST_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;
    private static final int MAX_PROMPT_CHARACTERS = 4000;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private TokenService tokenService;

    @Value("${app.request.max-body-bytes}")
    private int maxRequestBodyBytes;

    @Value("${app.request.max-topic-body-bytes}")
    private int maxTopicRequestBodyBytes;

    @BeforeEach
    void resetTheInterestTopicServiceStandIn() {
        INTEREST_TOPIC_SERVICE_STUB.resetMappings();
        INTEREST_TOPIC_SERVICE_STUB.resetRequests();
        stubEveryRequestWith(200, DOWNSTREAM_BODY);
    }

    @Nested
    class Authorization {

        @ParameterizedTest
        @EnumSource(Endpoint.class)
        void should_return_401_and_forward_nothing_when_there_is_no_cookie(Endpoint endpoint) {
            send(endpoint, null)
                    .expectStatus().isUnauthorized();

            assertNothingWasForwarded();
        }

        @ParameterizedTest
        @EnumSource(value = Endpoint.class, mode = EnumSource.Mode.EXCLUDE, names = {"LIST_CATEGORIES", "LIST_INTEREST_TOPICS"})
        void should_return_403_and_forward_nothing_when_a_user_calls_a_write(Endpoint endpoint) {
            send(endpoint, cookieFor(Role.USER))
                    .expectStatus().isForbidden();

            assertNothingWasForwarded();
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/categories/" + CATEGORY_ID, "/api/v1/interest-topics/" + INTEREST_TOPIC_ID})
        void should_return_403_and_forward_nothing_when_a_user_sends_a_method_outside_the_known_writes(String path) {
            restTestClient.put()
                    .uri(path)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.USER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"news\"}")
                    .exchange()
                    .expectStatus().isForbidden();

            assertNothingWasForwarded();
        }

        @ParameterizedTest
        @EnumSource(value = Endpoint.class, names = {"LIST_CATEGORIES", "LIST_INTEREST_TOPICS"})
        void should_forward_when_a_user_calls_a_read(Endpoint endpoint) {
            send(endpoint, cookieFor(Role.USER))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            assertForwardedOnce(endpoint);
        }

        @ParameterizedTest
        @EnumSource(value = Endpoint.class, mode = EnumSource.Mode.EXCLUDE, names = {"LIST_CATEGORIES", "LIST_INTEREST_TOPICS"})
        void should_forward_when_an_admin_calls_a_write(Endpoint endpoint) {
            send(endpoint, cookieFor(Role.ADMIN))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo(DOWNSTREAM_BODY);

            assertForwardedOnce(endpoint);
        }
    }

    @Nested
    class ForwardingFidelity {

        @Test
        void should_pass_the_downstream_status_body_and_content_type_through_verbatim() {
            String categories = "[{\"id\":\"" + CATEGORY_ID + "\",\"name\":\"tech\"}]";
            stubEveryRequestWith(203, categories);

            send(Endpoint.LIST_CATEGORIES, cookieFor(Role.USER))
                    .expectStatus().isEqualTo(203)
                    .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .expectBody(String.class).isEqualTo(categories);
        }

        @Test
        void should_pass_a_downstream_error_response_through_verbatim() {
            String conflict = "{\"status\":409,\"messages\":[\"Category 'tech' already exists.\"],\"timestamp\":\"2026-09-23T10:00:00Z\"}";
            stubEveryRequestWith(409, conflict);

            send(Endpoint.CREATE_CATEGORY, cookieFor(Role.ADMIN))
                    .expectStatus().isEqualTo(409)
                    .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .expectBody(String.class).isEqualTo(conflict);
        }

        @Test
        void should_forward_the_query_string_as_is() {
            String pathAndQuery = "/api/v1/interest-topics?page=1&size=5&categoryId=" + CATEGORY_ID;

            restTestClient.get()
                    .uri(pathAndQuery)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.USER))
                    .exchange()
                    .expectStatus().isOk();

            INTEREST_TOPIC_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(pathAndQuery)));
        }

        @ParameterizedTest
        @EnumSource(value = Endpoint.class, names = {"CREATE_CATEGORY", "RENAME_CATEGORY", "CREATE_INTEREST_TOPIC", "UPDATE_INTEREST_TOPIC"})
        void should_forward_the_request_body_and_content_type_as_is(Endpoint endpoint) {
            send(endpoint, cookieFor(Role.ADMIN))
                    .expectStatus().isOk();

            INTEREST_TOPIC_SERVICE_STUB.verifyThat(1, requestedFor(endpoint.method.name(), urlEqualTo(endpoint.path))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(REQUEST_CONTENT_TYPE))
                    .withRequestBody(equalToJson(endpoint.body)));
        }

        @Test
        void should_not_forward_the_cookie_or_the_authorization_header() {
            restTestClient.get()
                    .uri(Endpoint.LIST_CATEGORIES.path)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.USER))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer should-never-leave-the-gateway")
                    .exchange()
                    .expectStatus().isOk();

            INTEREST_TOPIC_SERVICE_STUB.verifyThat(1, requestedFor("GET", urlEqualTo(Endpoint.LIST_CATEGORIES.path))
                    .withoutHeader(HttpHeaders.COOKIE)
                    .withoutHeader(HttpHeaders.AUTHORIZATION));
        }
    }

    @Nested
    class UpstreamFailures {

        @Test
        void should_return_504_without_leaking_internals_when_the_downstream_times_out() {
            INTEREST_TOPIC_SERVICE_STUB.register(any(anyUrl())
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            ErrorResponseDTO body = send(Endpoint.LIST_CATEGORIES, cookieFor(Role.USER))
                    .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("Upstream service timed out.");
        }

        @Test
        void should_return_413_and_forward_nothing_for_a_body_over_the_size_cap() {
            String oversizedBody = "{\"name\":\"%s\"}".formatted("n".repeat(maxTopicRequestBodyBytes));

            restTestClient.post()
                    .uri(Endpoint.CREATE_CATEGORY.path)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(oversizedBody)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

            assertNothingWasForwarded();
        }

        @Test
        void should_forward_a_valid_topic_whose_utf8_body_exceeds_the_default_cap() {
            String prompt = "新".repeat(MAX_PROMPT_CHARACTERS);
            String body = "{\"name\":\"ai\",\"prompt\":\"%s\",\"categoryId\":\"%s\"}".formatted(prompt, CATEGORY_ID);
            assertThat(body.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(maxRequestBodyBytes);

            restTestClient.post()
                    .uri(Endpoint.CREATE_INTEREST_TOPIC.path)
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus().isOk();

            assertForwardedOnce(Endpoint.CREATE_INTEREST_TOPIC);
        }

        @Test
        void should_reject_and_forward_nothing_for_an_encoded_path_traversal() {
            restTestClient.get()
                    .uri(URI.create("/api/v1/categories/..%2F..%2Factuator"))
                    .cookie(CookieFactory.COOKIE_NAME, cookieFor(Role.USER))
                    .exchange()
                    .expectStatus().isBadRequest();

            assertNothingWasForwarded();
        }
    }

    @Nested
    class UnreachableUpstream {

        @DynamicPropertySource
        static void pointTheRoutesAtAClosedPort(DynamicPropertyRegistry registry) {
            registry.add("app.interest-topic-service.url", () -> "http://localhost:" + closedPort());
        }

        @Test
        void should_return_502_without_leaking_internals_when_the_downstream_refuses_the_connection() {
            ErrorResponseDTO body = send(Endpoint.LIST_CATEGORIES, cookieFor(Role.USER))
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

    private RestTestClient.ResponseSpec send(Endpoint endpoint, String cookie) {
        RestTestClient.RequestBodySpec request = restTestClient.method(endpoint.method)
                .uri(endpoint.path);

        if (cookie != null) {
            request.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        if (endpoint.body != null) {
            request.contentType(MediaType.parseMediaType(REQUEST_CONTENT_TYPE))
                    .body(endpoint.body);
        }

        return request.exchange();
    }

    private String cookieFor(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setRole(role);
        user.setCreatedAt(Instant.now());

        return tokenService.mint(user);
    }

    private void stubEveryRequestWith(int status, String body) {
        INTEREST_TOPIC_SERVICE_STUB.register(any(anyUrl())
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(body)));
    }

    private void assertNothingWasForwarded() {
        INTEREST_TOPIC_SERVICE_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
    }

    private void assertForwardedOnce(Endpoint endpoint) {
        INTEREST_TOPIC_SERVICE_STUB.verifyThat(1, requestedFor(endpoint.method.name(), urlEqualTo(endpoint.path)));
    }

    private enum Endpoint {

        CREATE_CATEGORY(HttpMethod.POST, "/api/v1/categories", "{\"name\":\"tech\"}"),
        LIST_CATEGORIES(HttpMethod.GET, "/api/v1/categories", null),
        RENAME_CATEGORY(HttpMethod.PATCH, "/api/v1/categories/" + CATEGORY_ID, "{\"name\":\"science\"}"),
        DELETE_CATEGORY(HttpMethod.DELETE, "/api/v1/categories/" + CATEGORY_ID, null),
        CREATE_INTEREST_TOPIC(HttpMethod.POST, "/api/v1/interest-topics",
                "{\"name\":\"ai\",\"prompt\":\"Latest AI research.\",\"categoryId\":\"" + CATEGORY_ID + "\"}"),
        LIST_INTEREST_TOPICS(HttpMethod.GET, "/api/v1/interest-topics", null),
        UPDATE_INTEREST_TOPIC(HttpMethod.PATCH, "/api/v1/interest-topics/" + INTEREST_TOPIC_ID, "{\"description\":\"Updated.\"}"),
        DELETE_INTEREST_TOPIC(HttpMethod.DELETE, "/api/v1/interest-topics/" + INTEREST_TOPIC_ID, null);

        private final HttpMethod method;

        private final String path;

        private final String body;

        Endpoint(HttpMethod method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }
}
