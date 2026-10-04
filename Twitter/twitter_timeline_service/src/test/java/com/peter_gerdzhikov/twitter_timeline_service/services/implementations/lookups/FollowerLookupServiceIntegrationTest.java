package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowerLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class FollowerLookupServiceIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Value("${app.internal-api.secret}")
    private String internalSecret;

    @Autowired
    private FollowerLookupService followerLookupService;

    @Nested
    class ForEachFollowerPage {

        @Test
        void should_hand_over_each_page_in_order_and_follow_the_cursor_when_there_are_several_pages() {
            UUID authorId = TestIds.userId();
            List<UUID> first = ids(2);
            List<UUID> second = ids(1);
            List<UUID> third = ids(3);
            stubFirstPage(authorId, first, "cursor-1");
            stubPage(authorId, "cursor-1", second, "cursor-2");
            stubPage(authorId, "cursor-2", third, null);

            List<List<UUID>> pages = collectPages(authorId);

            assertThat(pages).containsExactly(first, second, third);
        }

        @Test
        void should_hand_over_the_ids_once_when_there_is_a_single_page() {
            UUID authorId = TestIds.userId();
            List<UUID> only = ids(4);
            stubFirstPage(authorId, only, null);

            assertThat(collectPages(authorId)).containsExactly(only);
        }

        @Test
        void should_not_call_the_consumer_when_the_author_has_no_followers() {
            UUID authorId = TestIds.userId();
            stubFirstPage(authorId, List.of(), null);

            assertThat(collectPages(authorId)).isEmpty();
        }

        @Test
        void should_skip_an_empty_page_and_still_follow_its_cursor() {
            UUID authorId = TestIds.userId();
            List<UUID> last = ids(2);
            stubFirstPage(authorId, List.of(), "cursor-1");
            stubPage(authorId, "cursor-1", last, null);

            assertThat(collectPages(authorId)).containsExactly(last);
        }

        @Test
        void should_ask_for_pages_of_a_thousand_ids_and_send_the_internal_secret_on_every_request() {
            UUID authorId = TestIds.userId();
            stubFirstPage(authorId, ids(1), "cursor-1");
            stubPage(authorId, "cursor-1", ids(1), null);

            collectPages(authorId);

            GATEWAY_STUB.verifyThat(2, getRequestedFor(followerIdsPath(authorId))
                    .withQueryParam("size", equalTo("1000"))
                    .withHeader("X-Internal-Secret", equalTo(internalSecret)));
        }

        @Test
        void should_send_no_cursor_on_the_first_request_and_the_previous_cursor_on_the_next() {
            UUID authorId = TestIds.userId();
            stubFirstPage(authorId, ids(1), "cursor-1");
            stubPage(authorId, "cursor-1", ids(1), null);

            collectPages(authorId);

            GATEWAY_STUB.verifyThat(1, getRequestedFor(followerIdsPath(authorId)).withQueryParam("cursor", absent()));
            GATEWAY_STUB.verifyThat(1, getRequestedFor(followerIdsPath(authorId)).withQueryParam("cursor", equalTo("cursor-1")));
        }

        @Test
        void should_stop_paging_and_rethrow_when_the_consumer_fails() {
            UUID authorId = TestIds.userId();
            stubFirstPage(authorId, ids(1), "cursor-1");
            stubPage(authorId, "cursor-1", ids(1), null);

            assertThatThrownBy(() -> followerLookupService.forEachFollowerPage(authorId, page -> {
                throw new IllegalStateException("insert failed");
            })).isInstanceOf(IllegalStateException.class);

            GATEWAY_STUB.verifyThat(0, getRequestedFor(followerIdsPath(authorId)).withQueryParam("cursor", equalTo("cursor-1")));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_throw_unavailable_when_the_gateway_answers_5xx() {
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .willReturn(aResponse().withStatus(503).withBody("secret downstream detail")));

            assertThatThrownBy(() -> collectPages(authorId))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
        }

        @Test
        void should_throw_unavailable_when_the_gateway_answers_404() {
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId)).willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> collectPages(authorId)).isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_connection_is_reset() {
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> collectPages(authorId)).isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_answer_is_not_json() {
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("not json")));

            assertThatThrownBy(() -> collectPages(authorId)).isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_gateway_is_slower_than_the_read_timeout() {
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> collectPages(authorId))
                    .isInstanceOf(UpstreamTimeoutException.class)
                    .hasMessage("Upstream service timed out.");
        }

        @Test
        void should_keep_the_pages_already_handed_over_and_throw_when_a_later_page_fails() {
            UUID authorId = TestIds.userId();
            List<UUID> first = ids(2);
            stubFirstPage(authorId, first, "cursor-1");
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .withQueryParam("cursor", equalTo("cursor-1"))
                    .willReturn(aResponse().withStatus(500)));
            List<List<UUID>> handedOver = new ArrayList<>();

            assertThatThrownBy(() -> followerLookupService.forEachFollowerPage(authorId, handedOver::add))
                    .isInstanceOf(UpstreamUnavailableException.class);

            assertThat(handedOver).containsExactly(first);
        }
    }

    private List<List<UUID>> collectPages(UUID authorId) {
        List<List<UUID>> pages = new ArrayList<>();
        followerLookupService.forEachFollowerPage(authorId, pages::add);

        return pages;
    }

    private void stubFirstPage(UUID authorId, List<UUID> ids, String nextCursor) {
        GATEWAY_STUB.register(get(followerIdsPath(authorId))
                .withQueryParam("cursor", absent())
                .willReturn(pageResponse(ids, nextCursor)));
    }

    private void stubPage(UUID authorId, String cursor, List<UUID> ids, String nextCursor) {
        GATEWAY_STUB.register(get(followerIdsPath(authorId))
                .withQueryParam("cursor", equalTo(cursor))
                .willReturn(pageResponse(ids, nextCursor)));
    }

    private ResponseDefinitionBuilder pageResponse(List<UUID> ids, String nextCursor) {
        String idList = String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList());
        String cursor = nextCursor == null ? "null" : "\"" + nextCursor + "\"";

        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ids\":[" + idList + "],\"nextCursor\":" + cursor + "}");
    }

    private UrlPathPattern followerIdsPath(UUID authorId) {
        return urlPathEqualTo("/internal/v1/users/" + authorId + "/follower-ids");
    }

    private List<UUID> ids(int count) {
        return Stream
                .generate(TestIds::userId)
                .limit(count)
                .toList();
    }
}
