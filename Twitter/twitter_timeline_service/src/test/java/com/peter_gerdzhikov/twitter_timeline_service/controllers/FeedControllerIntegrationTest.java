package com.peter_gerdzhikov.twitter_timeline_service.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

class FeedControllerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String FEED_PATH = "/api/v1/feed";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String VIEWS_PATH = "/api/v1/views";

    private static final String SAVED_TWEETS_PATH = "/api/v1/saved-tweets";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Value("${app.internal-api.secret}")
    private String internalSecret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> tweetsHeldByTheTweetService = new ArrayList<>();

    private final List<String> usersKnownToTheGateway = new ArrayList<>();

    @Nested
    class Identity {

        @Test
        void should_return_400_when_the_user_id_header_is_missing() throws Exception {
            mockMvc
                    .perform(get(FEED_PATH))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", ""})
        void should_return_400_when_the_user_id_header_is_not_a_uuid(String header) throws Exception {
            mockMvc
                    .perform(get(FEED_PATH).header(USER_ID_HEADER, header))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Read {

        @Test
        void should_return_an_empty_page_when_the_feed_is_empty() throws Exception {
            JsonNode body = feed(TestIds.userId(), "", status().isOk());

            assertThat(body.get("items")).isEmpty();
            assertThat(body.get("nextCursor").isNull()).isTrue();
            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_return_the_newest_entries_first_when_the_feed_has_several() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID oldest = entry(owner, author, TWEET_CREATED_AT);
            UUID newest = entry(owner, author, TWEET_CREATED_AT.plusSeconds(2));
            UUID middle = entry(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(newest.toString(), middle.toString(), oldest.toString());
        }

        @Test
        void should_break_ties_on_the_tweet_time_by_tweet_id_when_entries_share_a_timestamp() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            entryWithId(owner, LOW_ID, author, TWEET_CREATED_AT);
            entryWithId(owner, HIGH_ID, author, TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(HIGH_ID.toString(), LOW_ID.toString());
        }

        @Test
        void should_return_the_tweet_and_the_author_from_the_stubs_when_an_entry_exists() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana", "/api/v1/files/pic-1");
            UUID tweetId = entry(owner, author, TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode item = feed(owner, "", status().isOk()).get("items").get(0);

            assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "views", "savedByMe", "content", "createdAt", "updatedAt", "images", "author");
            assertThat(item.get("id").asString()).isEqualTo(tweetId.toString());
            assertThat(item.get("content").asString()).isEqualTo("tweet " + tweetId);
            assertThat(item.get("createdAt").asString()).isEqualTo(TWEET_CREATED_AT.toString());
            assertThat(item.get("updatedAt").asString()).isEqualTo(TWEET_CREATED_AT.plusSeconds(1).toString());
            assertThat(item.get("images")).hasSize(1);
            assertThat(item.get("images").get(0).propertyNames()).containsExactlyInAnyOrder("id", "sizeBytes", "contentType");
            assertThat(item.get("images").get(0).get("sizeBytes").asLong()).isEqualTo(1234);
            assertThat(item.get("images").get(0).get("contentType").asString()).isEqualTo("image/png");
            assertThat(item.get("author").propertyNames()).containsExactlyInAnyOrder("id", "username", "profilePictureUrl");
            assertThat(item.get("author").get("id").asString()).isEqualTo(author.toString());
            assertThat(item.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(item.get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/pic-1");
        }

        @Test
        void should_return_a_null_picture_url_when_the_author_has_no_picture() throws Exception {
            UUID owner = TestIds.userId();
            entry(owner, knownAuthor("bob"), TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode author = feed(owner, "", status().isOk()).get("items").get(0).get("author");

            assertThat(author.get("profilePictureUrl").isNull()).isTrue();
        }

        @Test
        void should_return_only_the_callers_entries_when_other_users_have_entries() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID mine = entry(owner, author, TWEET_CREATED_AT);
            UUID someoneElses = entry(TestIds.userId(), author, TWEET_CREATED_AT.plusSeconds(1));
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(mine.toString());
            assertThat(requestedIds(TWEET_SERVICE_STUB, TWEETS_PATH)).doesNotContain(someoneElses.toString());
        }
    }

    @Nested
    class Paging {

        @Test
        void should_return_20_items_when_no_size_is_given() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            for (int index = 0; index < 25; index++) {
                entry(owner, author, TWEET_CREATED_AT.plusSeconds(index));
            }
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(body.get("items")).hasSize(20);
            assertThat(body.get("nextCursor").isNull()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "101", "-1"})
        void should_return_400_when_the_size_is_out_of_range(String size) throws Exception {
            JsonNode body = feed(TestIds.userId(), "?size=" + size, status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Page size must be between 1 and 100.");
        }

        @Test
        void should_return_400_when_the_size_is_not_a_number() throws Exception {
            feed(TestIds.userId(), "?size=many", status().isBadRequest());
        }

        @Test
        void should_visit_every_entry_exactly_once_when_walking_the_cursor() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(entry(owner, author, TWEET_CREATED_AT.plusSeconds(index)).toString());
            }
            stubDownstreams();

            List<String> seen = walk(owner, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_visit_every_entry_exactly_once_when_a_page_boundary_falls_inside_a_run_of_equal_timestamps() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(entry(owner, author, TWEET_CREATED_AT).toString());
            }
            stubDownstreams();

            List<String> seen = walk(owner, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @ParameterizedTest
        @ValueSource(strings = {"truncated", "padded", "uppercase-id", "not-a-cursor!"})
        void should_return_400_when_the_cursor_is_tampered_with_or_not_canonical(String kind) throws Exception {
            JsonNode body = feed(TestIds.userId(), "?cursor=" + cursorOfKind(kind), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Invalid cursor.");
        }

        private List<String> walk(UUID owner, int size) throws Exception {
            List<String> seen = new ArrayList<>();
            String query = "?size=" + size;
            while (true) {
                JsonNode page = feed(owner, query, status().isOk());
                seen.addAll(itemIds(page));
                if (page.get("nextCursor").isNull()) {
                    return seen;
                }

                query = "?size=" + size + "&cursor=" + page.get("nextCursor").asString();
            }
        }

        private String cursorOfKind(String kind) {
            String raw = "1767225600123456:" + LOW_ID;

            return switch (kind) {
                case "truncated" -> TimelineCursorCodec.encode(TWEET_CREATED_AT, LOW_ID).substring(0, 40);
                case "padded" -> Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                case "uppercase-id" -> Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(("1767225600123456:" + UUID.randomUUID().toString().toUpperCase()).getBytes(StandardCharsets.UTF_8));
                default -> kind;
            };
        }
    }

    @Nested
    class MissingData {

        @Test
        void should_skip_the_entry_and_still_advance_the_cursor_when_the_tweet_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = entry(owner, author, TWEET_CREATED_AT);
            entryWithoutTweet(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            UUID newest = entry(owner, author, TWEET_CREATED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode firstPage = feed(owner, "?size=2", status().isOk());
            JsonNode secondPage = feed(owner, "?size=2&cursor=" + firstPage.get("nextCursor").asString(), status().isOk());

            assertThat(itemIds(firstPage)).containsExactly(newest.toString());
            assertThat(firstPage.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(secondPage)).containsExactly(older.toString());
            assertThat(secondPage.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_skip_the_entry_and_still_advance_the_cursor_when_the_author_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = entry(owner, author, TWEET_CREATED_AT);
            entry(owner, TestIds.userId(), TWEET_CREATED_AT.plusSeconds(1));
            UUID newest = entry(owner, author, TWEET_CREATED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode firstPage = feed(owner, "?size=2", status().isOk());
            JsonNode secondPage = feed(owner, "?size=2&cursor=" + firstPage.get("nextCursor").asString(), status().isOk());

            assertThat(itemIds(firstPage)).containsExactly(newest.toString());
            assertThat(firstPage.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(secondPage)).containsExactly(older.toString());
        }

        @Test
        void should_return_an_empty_page_with_a_cursor_when_every_entry_of_the_page_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID kept = entry(owner, author, TWEET_CREATED_AT);
            entryWithoutTweet(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            entryWithoutTweet(owner, author, TWEET_CREATED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode page = feed(owner, "?size=2", status().isOk());

            assertThat(page.get("items")).isEmpty();
            assertThat(page.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(feed(owner, "?size=2&cursor=" + page.get("nextCursor").asString(), status().isOk())))
                    .containsExactly(kept.toString());
        }
    }

    @Nested
    class Calls {

        @Test
        void should_make_one_tweet_call_and_one_user_call_when_a_page_is_read() throws Exception {
            UUID owner = TestIds.userId();
            for (int index = 0; index < 5; index++) {
                entry(owner, knownAuthor("user" + index), TWEET_CREATED_AT.plusSeconds(index));
            }
            stubDownstreams();

            feed(owner, "?size=5", status().isOk());

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_send_only_the_ids_of_the_page_in_both_calls() throws Exception {
            UUID owner = TestIds.userId();
            UUID firstAuthor = knownAuthor("ana");
            UUID secondAuthor = knownAuthor("bob");
            UUID thirdAuthor = knownAuthor("cat");
            UUID oldest = entry(owner, thirdAuthor, TWEET_CREATED_AT);
            UUID middle = entry(owner, secondAuthor, TWEET_CREATED_AT.plusSeconds(1));
            UUID newest = entry(owner, firstAuthor, TWEET_CREATED_AT.plusSeconds(2));
            stubDownstreams();

            feed(owner, "?size=2", status().isOk());

            assertThat(requestedIds(TWEET_SERVICE_STUB, TWEETS_PATH))
                    .containsExactlyInAnyOrder(newest.toString(), middle.toString())
                    .doesNotContain(oldest.toString());
            assertThat(requestedIds(GATEWAY_STUB, USERS_PATH))
                    .containsExactlyInAnyOrder(firstAuthor.toString(), secondAuthor.toString())
                    .doesNotContain(thirdAuthor.toString());
        }

        @Test
        void should_ask_for_each_author_once_when_an_author_wrote_several_tweets_of_the_page() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            entry(owner, author, TWEET_CREATED_AT);
            entry(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            stubDownstreams();

            feed(owner, "", status().isOk());

            assertThat(requestedIds(GATEWAY_STUB, USERS_PATH)).containsExactly(author.toString());
        }

        @Test
        void should_send_the_internal_secret_on_the_user_call() throws Exception {
            UUID owner = TestIds.userId();
            entry(owner, knownAuthor("ana"), TWEET_CREATED_AT);
            stubDownstreams();

            feed(owner, "", status().isOk());

            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH))
                    .withHeader("X-Internal-Secret", equalTo(internalSecret)));
            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH))
                    .withoutHeader("X-Internal-Secret"));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_502_when_the_tweet_service_is_down() throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubUsersOnly();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = feed(owner, "", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_502_when_the_gateway_is_down() throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubTweetsOnly();
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = feed(owner, "", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout() throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubUsersOnly();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = feed(owner, "", status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @Test
        void should_return_504_when_the_gateway_is_slower_than_the_read_timeout() throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubTweetsOnly();
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = feed(owner, "", status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"tweet-service", "gateway"})
        void should_return_502_when_a_downstream_answers_5xx(String downstream) throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubDownstreams();
            failWith(downstream, aResponse().withStatus(503));

            feed(owner, "", status().isBadGateway());
        }

        @ParameterizedTest
        @ValueSource(strings = {"tweet-service", "gateway"})
        void should_not_leak_the_downstream_body_when_a_downstream_fails(String downstream) throws Exception {
            UUID owner = givenAFeedOfOneEntry();
            stubDownstreams();
            failWith(downstream, aResponse().withStatus(500).withBody("secret downstream detail"));

            MvcResult result = mockMvc
                    .perform(feedRequest(owner, ""))
                    .andExpect(status().isBadGateway())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("secret downstream detail")
                    .contains("Upstream service unavailable.");
        }

        private UUID givenAFeedOfOneEntry() {
            UUID owner = TestIds.userId();
            entry(owner, knownAuthor("ana"), TWEET_CREATED_AT);

            return owner;
        }

        private void failWith(String downstream, ResponseDefinitionBuilder response) {
            if (downstream.equals("tweet-service")) {
                TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).atPriority(1).willReturn(response));

            } else {
                GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH)).atPriority(1).willReturn(response));
            }
        }
    }

    @Nested
    class Views {

        @Test
        void should_return_the_view_count_of_each_item_when_tweets_have_views() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID lessViewed = entry(owner, author, TWEET_CREATED_AT);
            UUID moreViewed = entry(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            seedViews(lessViewed, 3);
            seedViews(moreViewed, 5);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemViews(body)).containsEntry(lessViewed.toString(), 3L).containsEntry(moreViewed.toString(), 5L);
        }

        @Test
        void should_return_zero_views_when_nobody_viewed_the_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = entry(owner, knownAuthor("ana"), TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemViews(body)).containsEntry(tweetId.toString(), 0L);
        }

        @Test
        void should_return_the_same_count_as_the_views_endpoint_when_a_tweet_has_views() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = entry(owner, knownAuthor("ana"), TWEET_CREATED_AT);
            seedViews(tweetId, 4);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemViews(body)).containsEntry(tweetId.toString(), viewsEndpointCountOf(owner, tweetId));
        }

        @Test
        void should_make_no_extra_downstream_call_when_views_are_added_to_the_page() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            seedViews(entry(owner, author, TWEET_CREATED_AT), 2);
            seedViews(entry(owner, author, TWEET_CREATED_AT.plusSeconds(1)), 2);
            stubDownstreams();

            feed(owner, "", status().isOk());

            assertThat(TWEET_SERVICE_STUB.find(anyRequestedFor(anyUrl()))).hasSize(1);
            assertThat(GATEWAY_STUB.find(anyRequestedFor(anyUrl()))).hasSize(1);
        }
    }

    @Nested
    class SavedByMe {

        @Test
        void should_mark_each_item_saved_only_when_the_caller_saved_its_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID saved = entry(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            UUID unsaved = entry(owner, author, TWEET_CREATED_AT);
            seedSavedTweet(owner, saved, author, TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemSavedByMe(body)).containsEntry(saved.toString(), true).containsEntry(unsaved.toString(), false);
        }

        @Test
        void should_mark_no_item_saved_when_the_caller_saved_nothing() throws Exception {
            UUID owner = TestIds.userId();
            entry(owner, knownAuthor("ana"), TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemSavedByMe(body).values()).containsOnly(false);
        }

        @Test
        void should_not_mark_an_item_saved_when_only_another_user_saved_its_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID tweetId = entry(owner, author, TWEET_CREATED_AT);
            seedSavedTweet(TestIds.userId(), tweetId, author, TWEET_CREATED_AT);
            stubDownstreams();

            JsonNode body = feed(owner, "", status().isOk());

            assertThat(itemSavedByMe(body)).containsEntry(tweetId.toString(), false);
        }

        @Test
        void should_mark_the_item_not_saved_when_the_caller_unsaves_its_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID tweetId = entry(owner, author, TWEET_CREATED_AT);
            seedSavedTweet(owner, tweetId, author, TWEET_CREATED_AT);
            stubDownstreams();
            assertThat(itemSavedByMe(feed(owner, "", status().isOk()))).containsEntry(tweetId.toString(), true);

            unsave(owner, tweetId);

            assertThat(itemSavedByMe(feed(owner, "", status().isOk()))).containsEntry(tweetId.toString(), false);
        }

        @Test
        void should_make_no_extra_downstream_call_when_the_saved_state_is_added_to_the_page() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            seedSavedTweet(owner, entry(owner, author, TWEET_CREATED_AT), author, TWEET_CREATED_AT);
            entry(owner, author, TWEET_CREATED_AT.plusSeconds(1));
            stubDownstreams();

            feed(owner, "", status().isOk());

            assertThat(TWEET_SERVICE_STUB.find(anyRequestedFor(anyUrl()))).hasSize(1);
            assertThat(GATEWAY_STUB.find(anyRequestedFor(anyUrl()))).hasSize(1);
        }
    }

    private MockHttpServletRequestBuilder feedRequest(UUID userId, String query) {
        return get(FEED_PATH + query).header(USER_ID_HEADER, userId.toString());
    }

    private JsonNode feed(UUID userId, String query, ResultMatcher expected) throws Exception {
        MvcResult result = mockMvc
                .perform(feedRequest(userId, query))
                .andExpect(expected)
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> itemIds(JsonNode page) {
        return page
                .get("items")
                .valueStream()
                .map(item -> item.get("id").asString())
                .toList();
    }

    private Map<String, Long> itemViews(JsonNode page) {
        Map<String, Long> views = new LinkedHashMap<>();
        page
                .get("items")
                .forEach(item -> views.put(item.get("id").asString(), item.get("views").asLong()));

        return views;
    }

    private Map<String, Boolean> itemSavedByMe(JsonNode page) {
        Map<String, Boolean> savedByMe = new LinkedHashMap<>();
        page
                .get("items")
                .forEach(item -> savedByMe.put(item.get("id").asString(), item.get("savedByMe").asBoolean()));

        return savedByMe;
    }

    private void unsave(UUID userId, UUID tweetId) throws Exception {
        mockMvc
                .perform(delete(SAVED_TWEETS_PATH + "/" + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNoContent());
    }

    private long viewsEndpointCountOf(UUID userId, UUID tweetId) throws Exception {
        MvcResult result = mockMvc
                .perform(get(VIEWS_PATH).header(USER_ID_HEADER, userId.toString()).param("tweetIds", tweetId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get(tweetId.toString())
                .asLong();
    }

    private List<String> requestedIds(WireMock stub, String path) {
        List<LoggedRequest> requests = stub.find(getRequestedFor(urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);

        return Stream
                .of(requests.getFirst().queryParameter("ids").firstValue().split(","))
                .toList();
    }

    /**
     * A user the gateway will know. Returns the id to give to {@link #entry}.
     */
    private UUID knownAuthor(String username) {
        return knownAuthor(username, null);
    }

    private UUID knownAuthor(String username, String profilePictureUrl) {
        UUID authorId = TestIds.userId();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", authorId);
        user.put("username", username);
        user.put("profilePictureUrl", profilePictureUrl);
        usersKnownToTheGateway.add(toJson(user));

        return authorId;
    }

    /**
     * A feed entry whose tweet the tweet service will return.
     */
    private UUID entry(UUID owner, UUID authorId, Instant tweetCreatedAt) {
        return entryWithId(owner, TestIds.tweetId(), authorId, tweetCreatedAt);
    }

    private UUID entryWithId(UUID owner, UUID tweetId, UUID authorId, Instant tweetCreatedAt) {
        seedEntry(owner, tweetId, authorId, tweetCreatedAt);
        tweetsHeldByTheTweetService.add(toJson(Map.of(
                "id", tweetId,
                "authorId", authorId,
                "content", "tweet " + tweetId,
                "views", 7,
                "createdAt", tweetCreatedAt.toString(),
                "updatedAt", tweetCreatedAt.plusSeconds(1).toString(),
                "images", List.of(Map.of("id", UUID.randomUUID(), "sizeBytes", 1234, "contentType", "image/png")))));

        return tweetId;
    }

    /**
     * A feed entry whose tweet was deleted: the tweet service no longer knows it.
     */
    private UUID entryWithoutTweet(UUID owner, UUID authorId, Instant tweetCreatedAt) {
        UUID tweetId = TestIds.tweetId();
        seedEntry(owner, tweetId, authorId, tweetCreatedAt);

        return tweetId;
    }

    private void stubDownstreams() {
        stubTweetsOnly();
        stubUsersOnly();
    }

    private void stubTweetsOnly() {
        TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).willReturn(jsonArray(tweetsHeldByTheTweetService)));
    }

    private void stubUsersOnly() {
        GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH)).willReturn(jsonArray(usersKnownToTheGateway)));
    }

    private ResponseDefinitionBuilder jsonArray(List<String> elements) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[" + String.join(",", elements) + "]");
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
