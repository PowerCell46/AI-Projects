package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class UserFollowedListenerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final Instant FOLLOWED_AT = Instant.parse("2026-01-05T00:00:00.123Z");

    private static final Instant NEWER = FOLLOWED_AT.minus(Duration.ofHours(1));

    private static final Instant OLDER = FOLLOWED_AT.minus(Duration.ofDays(2));

    @Autowired
    private MutableClock clock;

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    @Nested
    class Backfill {

        @Test
        void should_insert_the_followees_recent_tweets_into_the_followers_feed_newest_first_when_the_user_follows() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID newer = TestIds.tweetId();
            UUID older = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(newer, NEWER), new AuthorTweet(older, OLDER)));
            stubFollowExists(followerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));

            awaitFeedHolds(followerId, older);
            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(newer, older);
        }

        @Test
        void should_store_the_author_and_the_tweet_time_in_each_entry_when_the_user_follows() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowExists(followerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(followerId, tweetId);

            assertThat(feedEntryRepository.findFirstPage(followerId, PageRequest.of(0, 10)))
                    .singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.getAuthorId()).isEqualTo(followeeId);
                        assertThat(entry.getTweetCreatedAt()).isEqualTo(NEWER);
                    });
        }

        @Test
        void should_ask_for_the_newest_fifty_tweets_since_seven_days_before_now() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            clock.setInstant(FOLLOWED_AT);
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowExists(followerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(followerId, tweetId);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(tweetsByAuthorPath(followeeId))
                    .withQueryParam("since", equalTo(FOLLOWED_AT.minus(Duration.ofDays(7)).toString()))
                    .withQueryParam("limit", equalTo("50")));
        }

        @Test
        void should_still_ask_since_seven_days_before_now_when_the_event_time_is_far_in_the_past() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            clock.setInstant(FOLLOWED_AT);
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowExists(followerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, Instant.MIN));
            awaitFeedHolds(followerId, tweetId);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(tweetsByAuthorPath(followeeId))
                    .withQueryParam("since", equalTo(FOLLOWED_AT.minus(Duration.ofDays(7)).toString())));
        }

        @Test
        void should_insert_no_more_than_fifty_tweets_when_the_tweet_service_answers_with_more() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            List<AuthorTweet> fiftyOne = IntStream
                    .range(0, 51)
                    .mapToObj(minutesAgo -> new AuthorTweet(TestIds.tweetId(), NEWER.minus(Duration.ofMinutes(minutesAgo))))
                    .toList();
            stubTweetsByAuthor(followeeId, fiftyOne);
            stubFollowExists(followerId, followeeId);
            stubFollowExists(sentinelFollowerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(sentinelFollowerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, fiftyOne.get(49).getId());

            assertThat(tweetIdsInFeedOf(followerId))
                    .hasSize(50)
                    .doesNotContain(fiftyOne.get(50).getId());
        }

        @Test
        void should_leave_other_users_feeds_alone_when_a_user_follows() {
            UUID followerId = TestIds.userId();
            UUID otherUserId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowExists(followerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(followerId, tweetId);

            assertThat(tweetIdsInFeedOf(otherUserId)).isEmpty();
        }

        @Test
        void should_add_nothing_when_the_same_event_is_redelivered() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowExists(followerId, followeeId);
            stubFollowExists(sentinelFollowerId, followeeId);
            String event = userFollowedJson(followerId, followeeId, FOLLOWED_AT);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), event);
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), event);
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(sentinelFollowerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, tweetId);

            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(tweetId);
        }

        @Test
        void should_skip_the_follow_check_when_the_followee_has_no_recent_tweets() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID sentinelFolloweeId = TestIds.userId();
            UUID sentinelTweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of());
            stubTweetsByAuthor(sentinelFolloweeId, List.of(new AuthorTweet(sentinelTweetId, NEWER)));
            stubFollowExists(sentinelFollowerId, sentinelFolloweeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            publish(USER_FOLLOWED_TOPIC, sentinelFolloweeId.toString(),
                    userFollowedJson(sentinelFollowerId, sentinelFolloweeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, sentinelTweetId);

            assertThat(tweetIdsInFeedOf(followerId)).isEmpty();
            GATEWAY_STUB.verifyThat(0, getRequestedFor(followCheckPath(followerId, followeeId)));
        }
    }

    @Nested
    class UnfollowedMeanwhile {

        @Test
        void should_leave_none_of_the_followees_entries_when_the_follow_is_gone_at_the_check() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowMissing(followerId, followeeId);
            stubFollowExists(sentinelFollowerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(sentinelFollowerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, tweetId);

            assertThat(tweetIdsInFeedOf(followerId)).isEmpty();
        }

        @Test
        void should_keep_other_authors_entries_when_the_follow_is_gone_at_the_check() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID byAnotherAuthor = TestIds.tweetId();
            UUID tweetId = TestIds.tweetId();
            seedEntry(followerId, byAnotherAuthor, TestIds.userId(), OLDER);
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            stubFollowMissing(followerId, followeeId);
            stubFollowExists(sentinelFollowerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(sentinelFollowerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, tweetId);

            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(byAnotherAuthor);
        }
    }

    @Nested
    class Retries {

        @Test
        void should_retry_from_the_top_and_end_with_the_entries_once_when_the_follow_check_fails_then_recovers() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(tweetId, NEWER)));
            String scenario = "flaky-" + followerId;
            GATEWAY_STUB.register(get(followCheckPath(followerId, followeeId))
                    .inScenario(scenario)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("recovered"));
            GATEWAY_STUB.register(get(followCheckPath(followerId, followeeId))
                    .inScenario(scenario)
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"following\":true}")));

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));

            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> GATEWAY_STUB.verifyThat(
                            moreThanOrExactly(2), getRequestedFor(followCheckPath(followerId, followeeId))));
            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(tweetId);
        }

        @Test
        void should_dead_letter_the_record_when_the_tweet_service_stays_down_past_the_retries() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            TWEET_SERVICE_STUB.register(get(tweetsByAuthorPath(followeeId)).willReturn(aResponse().withStatus(503)));

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));

            awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, followeeId.toString());
            TWEET_SERVICE_STUB.verifyThat(moreThanOrExactly(2), getRequestedFor(tweetsByAuthorPath(followeeId)));
            assertThat(tweetIdsInFeedOf(followerId)).isEmpty();
        }

        @Test
        void should_dead_letter_the_record_when_the_gateway_stays_down_past_the_retries() {
            UUID followerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(TestIds.tweetId(), NEWER)));
            GATEWAY_STUB.register(get(followCheckPath(followerId, followeeId)).willReturn(aResponse().withStatus(503)));

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(followerId, followeeId, FOLLOWED_AT));

            awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, followeeId.toString());
            GATEWAY_STUB.verifyThat(moreThanOrExactly(2), getRequestedFor(followCheckPath(followerId, followeeId)));
        }
    }

    @Nested
    class InvalidEvents {

        @Test
        void should_dead_letter_without_retry_or_a_downstream_call_when_the_event_is_invalid() {
            UUID followerId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID followeeId = TestIds.userId();
            UUID sentinelTweetId = TestIds.tweetId();
            String withoutOccurredAt = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"followerId\":\"" + followerId
                    + "\",\"followeeId\":\"" + followeeId + "\",\"followeeEmail\":\"bob@example.com\"}";
            stubTweetsByAuthor(followeeId, List.of(new AuthorTweet(sentinelTweetId, NEWER)));
            stubFollowExists(sentinelFollowerId, followeeId);

            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), withoutOccurredAt);
            publish(USER_FOLLOWED_TOPIC, followeeId.toString(), userFollowedJson(sentinelFollowerId, followeeId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, sentinelTweetId);

            awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, followeeId.toString());
            verify(feedBackfillService, times(2)).backfill(any(UserFollowedEventDTO.class));
            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(tweetsByAuthorPath(followeeId)));
            assertThat(tweetIdsInFeedOf(followerId)).isEmpty();
        }

        @Test
        void should_dead_letter_without_retry_or_a_downstream_call_and_keep_the_feed_when_the_user_follows_themselves() {
            UUID userId = TestIds.userId();
            UUID sentinelFollowerId = TestIds.userId();
            UUID ownTweetId = TestIds.tweetId();
            UUID sentinelTweetId = TestIds.tweetId();
            seedEntry(userId, ownTweetId, userId, OLDER);
            stubTweetsByAuthor(userId, List.of(new AuthorTweet(sentinelTweetId, NEWER)));
            stubFollowExists(sentinelFollowerId, userId);

            publish(USER_FOLLOWED_TOPIC, userId.toString(), userFollowedJson(userId, userId, FOLLOWED_AT));
            publish(USER_FOLLOWED_TOPIC, userId.toString(), userFollowedJson(sentinelFollowerId, userId, FOLLOWED_AT));
            awaitFeedHolds(sentinelFollowerId, sentinelTweetId);

            awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, userId.toString());
            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(tweetsByAuthorPath(userId)));
            assertThat(tweetIdsInFeedOf(userId)).containsExactly(ownTweetId);
        }
    }
}
