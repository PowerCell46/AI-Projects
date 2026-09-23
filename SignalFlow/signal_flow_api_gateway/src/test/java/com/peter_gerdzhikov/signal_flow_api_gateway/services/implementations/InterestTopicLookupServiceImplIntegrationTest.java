package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.interesttopics.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.InterestTopicFeedMode;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.interesttopics.InterestTopicFeedUnavailableException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.interesttopics.InterestTopicLookupFailedException;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractInterestTopicServiceIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class InterestTopicLookupServiceImplIntegrationTest extends AbstractInterestTopicServiceIntegrationTest {

    private static final String EXISTING_PATH = "/internal/v1/interest-topics/existing";

    private static final String FEED_PATH = "/internal/v1/interest-topics/feed";

    private static final UUID EXISTING_ID = UUID.fromString("7d2e9f10-3c4b-4a5d-8e6f-1a2b3c4d5e6f");

    private static final UUID MISSING_ID = UUID.fromString("0b5c3a4e-6f0e-4b8e-9d3a-2c1f7e8a9b01");

    private static final InterestTopicFeedRequestDTO FEED_REQUEST =
            new InterestTopicFeedRequestDTO(List.of(EXISTING_ID), InterestTopicFeedMode.INCLUDE, "go", 20);

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final String FEED_BODY = """
            {"items": [{"id": "%s", "name": "rust"}], "nextCursor": "rust", "total": 5, "matching": 1}
            """.formatted(UUID.randomUUID());

    @Autowired
    private InterestTopicLookupService interestTopicLookupService;

    @BeforeEach
    void resetTheInterestTopicServiceStandIn() {
        INTEREST_TOPIC_SERVICE_STUB.resetMappings();
        INTEREST_TOPIC_SERVICE_STUB.resetRequests();
    }

    @Nested
    class FindExistingIds {

        @Test
        void should_post_the_ids_as_json_to_the_existence_endpoint() {
            stubExistenceLookup(existingIdsResponse(EXISTING_ID));

            interestTopicLookupService.findExistingIds(List.of(EXISTING_ID, MISSING_ID));

            INTEREST_TOPIC_SERVICE_STUB.verifyThat(postRequestedFor(urlEqualTo(EXISTING_PATH))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(equalToJson("{\"ids\": [\"%s\", \"%s\"]}".formatted(EXISTING_ID, MISSING_ID))));
        }

        @Test
        void should_return_the_existing_ids_from_the_response() {
            stubExistenceLookup(existingIdsResponse(EXISTING_ID));

            Set<UUID> existing = interestTopicLookupService.findExistingIds(List.of(EXISTING_ID, MISSING_ID));

            assertThat(existing).containsExactly(EXISTING_ID);
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 404, 500, 503})
        void should_fail_rather_than_report_none_existing_when_the_response_is_not_2xx(int status) {
            stubExistenceLookup(aResponse().withStatus(status));

            assertThatThrownBy(() -> interestTopicLookupService.findExistingIds(List.of(EXISTING_ID)))
                    .isInstanceOf(InterestTopicLookupFailedException.class);
        }

        @Test
        void should_fail_rather_than_report_none_existing_when_the_response_has_no_existing_ids() {
            stubExistenceLookup(jsonResponse("{}"));

            assertThatThrownBy(() -> interestTopicLookupService.findExistingIds(List.of(EXISTING_ID)))
                    .isInstanceOf(InterestTopicLookupFailedException.class);
        }

        @Test
        void should_fail_rather_than_report_none_existing_when_the_response_times_out() {
            stubExistenceLookup(existingIdsResponse(EXISTING_ID)
                    .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS));

            assertThatThrownBy(() -> interestTopicLookupService.findExistingIds(List.of(EXISTING_ID)))
                    .isInstanceOf(InterestTopicLookupFailedException.class);
        }
    }

    @Nested
    class FindFeedPage {

        @Test
        void should_post_the_request_as_json_to_the_feed_endpoint() {
            stubFeed(jsonResponse(FEED_BODY));

            interestTopicLookupService.findFeedPage(FEED_REQUEST);

            INTEREST_TOPIC_SERVICE_STUB.verifyThat(postRequestedFor(urlEqualTo(FEED_PATH))
                    .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(equalToJson(
                            "{\"ids\": [\"%s\"], \"mode\": \"INCLUDE\", \"after\": \"go\", \"size\": 20}"
                                    .formatted(EXISTING_ID))));
        }

        @Test
        void should_return_the_page_from_the_response() {
            stubFeed(jsonResponse(FEED_BODY));

            InterestTopicFeedResponseDTO page = interestTopicLookupService.findFeedPage(FEED_REQUEST);

            assertThat(page.getItems()).extracting(InterestTopicResponseDTO::getName).containsExactly("rust");
            assertThat(page.getNextCursor()).isEqualTo("rust");
            assertThat(page.getTotal()).isEqualTo(5);
            assertThat(page.getMatching()).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 404, 500, 503})
        void should_fail_when_the_response_is_not_2xx(int status) {
            stubFeed(aResponse().withStatus(status));

            assertThatThrownBy(() -> interestTopicLookupService.findFeedPage(FEED_REQUEST))
                    .isInstanceOf(InterestTopicFeedUnavailableException.class);
        }

        @Test
        void should_fail_when_the_response_has_no_items() {
            stubFeed(jsonResponse("{}"));

            assertThatThrownBy(() -> interestTopicLookupService.findFeedPage(FEED_REQUEST))
                    .isInstanceOf(InterestTopicFeedUnavailableException.class);
        }

        @Test
        void should_fail_when_the_response_times_out() {
            stubFeed(jsonResponse(FEED_BODY).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS));

            assertThatThrownBy(() -> interestTopicLookupService.findFeedPage(FEED_REQUEST))
                    .isInstanceOf(InterestTopicFeedUnavailableException.class);
        }
    }

    @Nested
    class UnreachableInterestTopicService {

        @DynamicPropertySource
        static void pointTheClientAtAClosedPort(DynamicPropertyRegistry registry) {
            registry.add("app.interest-topic-service.url", () -> "http://localhost:" + closedPort());
        }

        @Test
        void should_fail_rather_than_report_none_existing_when_the_connection_is_refused() {
            assertThatThrownBy(() -> interestTopicLookupService.findExistingIds(List.of(EXISTING_ID)))
                    .isInstanceOf(InterestTopicLookupFailedException.class);
        }

        @Test
        void should_fail_the_feed_page_when_the_connection_is_refused() {
            assertThatThrownBy(() -> interestTopicLookupService.findFeedPage(FEED_REQUEST))
                    .isInstanceOf(InterestTopicFeedUnavailableException.class);
        }
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void stubExistenceLookup(ResponseDefinitionBuilder response) {
        INTEREST_TOPIC_SERVICE_STUB.register(post(urlEqualTo(EXISTING_PATH)).willReturn(response));
    }

    private void stubFeed(ResponseDefinitionBuilder response) {
        INTEREST_TOPIC_SERVICE_STUB.register(post(urlEqualTo(FEED_PATH)).willReturn(response));
    }

    private ResponseDefinitionBuilder existingIdsResponse(UUID existingId) {
        return jsonResponse("{\"existingIds\": [\"%s\"]}".formatted(existingId));
    }

    private ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(body);
    }
}
