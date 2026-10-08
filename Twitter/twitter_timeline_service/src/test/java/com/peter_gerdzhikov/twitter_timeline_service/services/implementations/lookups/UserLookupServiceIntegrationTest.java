package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class UserLookupServiceIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final String USERS_PATH = "/internal/v1/users";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Value("${app.internal-api.secret}")
    private String internalSecret;

    @Autowired
    private UserLookupService userLookupService;

    @Nested
    class FindByIds {

        @Test
        void should_return_the_users_by_id_with_their_username_and_picture_url_when_the_gateway_knows_them() {
            UUID withPicture = TestIds.userId();
            UUID withoutPicture = TestIds.userId();
            stubUsers("[{\"id\":\"" + withPicture + "\",\"username\":\"ana\",\"profilePictureUrl\":\"/api/v1/files/f1\"},"
                    + "{\"id\":\"" + withoutPicture + "\",\"username\":\"bob\",\"profilePictureUrl\":null}]");

            Map<UUID, UserClientDTO> users = userLookupService.findByIds(List.of(withPicture, withoutPicture));

            assertThat(users).containsOnlyKeys(withPicture, withoutPicture);
            assertThat(users.get(withPicture).getUsername()).isEqualTo("ana");
            assertThat(users.get(withPicture).getProfilePictureUrl()).isEqualTo("/api/v1/files/f1");
            assertThat(users.get(withoutPicture).getProfilePictureUrl()).isNull();
        }

        @Test
        void should_leave_out_an_id_the_gateway_does_not_return() {
            UUID known = TestIds.userId();
            UUID unknown = TestIds.userId();
            stubUsers("[{\"id\":\"" + known + "\",\"username\":\"ana\",\"profilePictureUrl\":null}]");

            assertThat(userLookupService.findByIds(List.of(known, unknown))).containsOnlyKeys(known);
        }

        @Test
        void should_ignore_fields_it_does_not_know_when_the_gateway_sends_more() {
            UUID id = TestIds.userId();
            stubUsers("[{\"id\":\"" + id + "\",\"username\":\"ana\",\"profilePictureUrl\":null,\"bio\":\"hi\"}]");

            assertThat(userLookupService.findByIds(List.of(id))).containsOnlyKeys(id);
        }

        @Test
        void should_send_the_ids_in_one_comma_separated_parameter_and_the_internal_secret() {
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();
            stubUsers("[]");

            userLookupService.findByIds(List.of(first, second));

            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH))
                    .withQueryParam("ids", equalTo(first + "," + second))
                    .withHeader("X-Internal-Secret", equalTo(internalSecret)));
        }

        @Test
        void should_make_no_call_when_there_are_no_ids() {
            assertThat(userLookupService.findByIds(List.of())).isEmpty();

            GATEWAY_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_throw_unavailable_when_the_gateway_answers_5xx() {
            GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("secret downstream detail")));

            assertThatThrownBy(() -> userLookupService.findByIds(List.of(TestIds.userId())))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
        }

        @Test
        void should_throw_unavailable_when_the_gateway_answers_404() {
            GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> userLookupService.findByIds(List.of(TestIds.userId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_connection_is_reset() {
            GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> userLookupService.findByIds(List.of(TestIds.userId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_answer_is_not_a_json_array() {
            stubUsers("{\"unexpected\":true}");

            assertThatThrownBy(() -> userLookupService.findByIds(List.of(TestIds.userId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_gateway_is_slower_than_the_read_timeout() {
            GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> userLookupService.findByIds(List.of(TestIds.userId())))
                    .isInstanceOf(UpstreamTimeoutException.class)
                    .hasMessage("Upstream service timed out.");
        }
    }

    private void stubUsers(String body) {
        GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
