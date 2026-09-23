package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.SubscribeRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.UserResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractInterestTopicServiceIntegrationTest;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class FeedControllerIntegrationTest extends AbstractInterestTopicServiceIntegrationTest {

    private static final String FEED_PATH = "/api/v1/feed";
    private static final String TOPIC_SERVICE_FEED_PATH = "/internal/v1/interest-topics/feed";
    private static final String EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "Password123";
    private static final UUID SUBSCRIBED_TOPIC_ID = UUID.fromString("7d2e9f10-3c4b-4a5d-8e6f-1a2b3c4d5e6f");
    private static final UUID OTHER_TOPIC_ID = UUID.fromString("0b5c3a4e-6f0e-4b8e-9d3a-2c1f7e8a9b01");
    private static final UUID CATEGORY_ID = UUID.fromString("5a1d0c2e-8b3f-4e6a-9c7d-3f2e1b0a9c8d");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void resetState() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        INTEREST_TOPIC_SERVICE_STUB.resetMappings();
        INTEREST_TOPIC_SERVICE_STUB.resetRequests();
        stubTopicServiceFeed(jsonResponse(twoTopicPage()));
    }

    @Nested
    class FindFeed {

        @Test
        void should_return_401_and_call_nothing_when_there_is_no_cookie() {
            restTestClient.get()
                    .uri(FEED_PATH)
                    .exchange()
                    .expectStatus().isUnauthorized();

            assertTheTopicServiceWasNotCalled();
        }

        @Test
        void should_return_the_topics_flagged_by_subscription_with_counts_and_a_cursor() {
            String cookie = registerAndGetCookie(EMAIL);
            subscribe(cookie, SUBSCRIBED_TOPIC_ID);

            getFeed(cookie, "")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.items.length()").isEqualTo(2)
                    .jsonPath("$.items[0].name").isEqualTo("go")
                    .jsonPath("$.items[0].categoryName").isEqualTo("programming")
                    .jsonPath("$.items[0].subscribed").isEqualTo(true)
                    .jsonPath("$.items[1].subscribed").isEqualTo(false)
                    .jsonPath("$.nextCursor").isEqualTo("rust")
                    .jsonPath("$.counts.all").isEqualTo(5)
                    .jsonPath("$.counts.subscribed").isEqualTo(1)
                    .jsonPath("$.counts.notSubscribed").isEqualTo(4);
        }

        @Test
        void should_not_expose_the_prompt_or_the_creation_time() {
            String cookie = registerAndGetCookie(EMAIL);

            getFeed(cookie, "")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.items[0].prompt").doesNotExist()
                    .jsonPath("$.items[0].createdAt").doesNotExist();
        }

        @Test
        void should_default_to_every_topic_from_the_start_in_pages_of_20() {
            String cookie = registerAndGetCookie(EMAIL);

            getFeed(cookie, "").expectStatus().isOk();

            assertTheTopicServiceWasAskedFor("{\"ids\": [], \"mode\": \"ALL\", \"after\": null, \"size\": 20}");
        }

        @ParameterizedTest
        @CsvSource({"ALL, ALL", "SUBSCRIBED, INCLUDE", "NOT_SUBSCRIBED, EXCLUDE"})
        void should_send_the_callers_subscribed_ids_with_the_mode_for_the_filter(String filter, String mode) {
            String cookie = registerAndGetCookie(EMAIL);
            subscribe(cookie, SUBSCRIBED_TOPIC_ID);

            getFeed(cookie, "?filter=%s&after=go&size=5".formatted(filter)).expectStatus().isOk();

            assertTheTopicServiceWasAskedFor("{\"ids\": [\"%s\"], \"mode\": \"%s\", \"after\": \"go\", \"size\": 5}"
                    .formatted(SUBSCRIBED_TOPIC_ID, mode));
        }

        @Test
        void should_not_send_another_users_subscriptions() {
            String cookie = registerAndGetCookie(EMAIL);
            String otherCookie = registerAndGetCookie(OTHER_EMAIL);
            subscribe(otherCookie, SUBSCRIBED_TOPIC_ID);

            getFeed(cookie, "?filter=SUBSCRIBED").expectStatus().isOk();

            assertTheTopicServiceWasAskedFor("{\"ids\": [], \"mode\": \"INCLUDE\", \"after\": null, \"size\": 20}");
        }

        @ParameterizedTest
        @ValueSource(strings = {"?size=0", "?size=101", "?size=abc", "?filter=EVERYTHING", "?filter=subscribed"})
        void should_return_400_and_call_nothing_for_an_invalid_parameter(String query) {
            String cookie = registerAndGetCookie(EMAIL);

            getFeed(cookie, query).expectStatus().isBadRequest();

            assertTheTopicServiceWasNotCalled();
        }

        @Test
        void should_return_400_and_call_nothing_for_a_cursor_over_100_characters() {
            String cookie = registerAndGetCookie(EMAIL);

            getFeed(cookie, "?after=" + "a".repeat(101)).expectStatus().isBadRequest();

            assertTheTopicServiceWasNotCalled();
        }

        @Test
        void should_return_502_when_the_topic_service_fails() {
            String cookie = registerAndGetCookie(EMAIL);
            stubTopicServiceFeed(aResponse().withStatus(500));

            getFeed(cookie, "")
                    .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                    .expectBody()
                    .jsonPath("$.messages[0]").isEqualTo("Upstream service unavailable.");
        }
    }

    private RestTestClient.ResponseSpec getFeed(String cookie, String query) {
        return restTestClient.get()
                .uri(FEED_PATH + query)
                .cookie(CookieFactory.COOKIE_NAME, cookie)
                .exchange();
    }

    private void subscribe(String cookie, UUID interestTopicId) {
        SubscribeRequestDTO request = new SubscribeRequestDTO();
        request.setInterestTopicId(interestTopicId);

        restTestClient.post()
                .uri("/api/v1/subscriptions")
                .cookie(CookieFactory.COOKIE_NAME, cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated();
    }

    private String registerAndGetCookie(String email) {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        return restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponseDTO.class)
                .returnResult()
                .getResponseCookies()
                .getFirst(CookieFactory.COOKIE_NAME)
                .getValue();
    }

    private void stubTopicServiceFeed(ResponseDefinitionBuilder response) {
        INTEREST_TOPIC_SERVICE_STUB.register(post(urlEqualTo(TOPIC_SERVICE_FEED_PATH)).willReturn(response));
    }

    private void assertTheTopicServiceWasAskedFor(String expectedBody) {
        INTEREST_TOPIC_SERVICE_STUB.verifyThat(1, postRequestedFor(urlEqualTo(TOPIC_SERVICE_FEED_PATH))
                .withRequestBody(equalToJson(expectedBody)));
    }

    private void assertTheTopicServiceWasNotCalled() {
        INTEREST_TOPIC_SERVICE_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
    }

    private String twoTopicPage() {
        return """
                {
                  "items": [
                    {"id": "%s", "name": "go", "description": "A language.", "categoryId": "%s",
                     "categoryName": "programming", "createdAt": "2026-09-23T10:00:00Z"},
                    {"id": "%s", "name": "rust", "description": "A language.", "categoryId": "%s",
                     "categoryName": "programming", "createdAt": "2026-09-23T10:00:00Z"}
                  ],
                  "nextCursor": "rust",
                  "total": 5,
                  "matching": 1
                }
                """.formatted(SUBSCRIBED_TOPIC_ID, CATEGORY_ID, OTHER_TOPIC_ID, CATEGORY_ID);
    }

    private ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(body);
    }
}
