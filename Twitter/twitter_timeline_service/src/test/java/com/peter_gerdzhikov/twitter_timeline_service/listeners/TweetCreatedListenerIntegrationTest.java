package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetCreatedListenerIntegrationTest extends AbstractListenerIntegrationTest {

    @Autowired
    private MutableClock clock;

    @Value("${app.feed.retention}")
    private Duration retention;

    @BeforeEach
    void pinTheClockNextToTheTweets() {
        clock.setInstant(TWEET_CREATED_AT.plusSeconds(1));
    }

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    @Nested
    class FanOut {

        @Test
        void should_create_an_entry_for_the_author_and_every_follower_when_a_tweet_is_created_across_several_follower_pages() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> firstPage = newUsers(2);
            List<UUID> secondPage = newUsers(2);
            List<UUID> thirdPage = newUsers(1);
            stubFollowerPages(authorId, List.of(firstPage, secondPage, thirdPage));
            UUID stranger = TestIds.userId();

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT));

            List<UUID> everyone = new ArrayList<>(List.of(authorId));
            everyone.addAll(firstPage);
            everyone.addAll(secondPage);
            everyone.addAll(thirdPage);
            everyone.forEach(userId -> awaitFeedHolds(userId, tweetId));
            everyone.forEach(userId -> assertThat(tweetIdsInFeedOf(userId)).containsExactly(tweetId));
            assertThat(tweetIdsInFeedOf(stranger)).isEmpty();
        }

        @Test
        void should_store_the_author_and_the_tweet_time_in_each_entry_when_a_tweet_is_created() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID follower = TestIds.userId();
            stubFollowerPages(authorId, List.of(List.of(follower)));

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT));
            awaitFeedHolds(follower, tweetId);

            assertThat(feedEntryRepository.findFirstPage(follower, PageRequest.of(0, 10)))
                    .singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.getAuthorId()).isEqualTo(authorId);
                        assertThat(entry.getTweetCreatedAt()).isEqualTo(TWEET_CREATED_AT);
                    });
        }

        @Test
        void should_add_nothing_and_ask_the_gateway_for_nothing_when_the_tweet_is_older_than_the_retention() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID follower = TestIds.userId();
            stubFollowerPages(authorId, List.of(List.of(follower)));
            Instant tooOld = clock.instant().minus(retention).minusSeconds(1);
            UUID sentinelTweetId = TestIds.tweetId();

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, tooOld));
            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(sentinelTweetId, authorId, TWEET_CREATED_AT));
            awaitFeedHolds(authorId, sentinelTweetId);

            assertThat(tweetIdsInFeedOf(authorId)).containsExactly(sentinelTweetId);
            assertThat(tweetIdsInFeedOf(follower)).containsExactly(sentinelTweetId);
            GATEWAY_STUB.verifyThat(exactly(1), getRequestedFor(followerIdsPath(authorId)));
        }

        @Test
        void should_add_nothing_when_the_same_event_is_redelivered() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> followers = newUsers(3);
            stubFollowerPages(authorId, List.of(followers));
            String event = tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT);
            UUID sentinelTweetId = TestIds.tweetId();

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), event);
            publish(TWEET_CREATED_TOPIC, tweetId.toString(), event);
            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(sentinelTweetId, authorId, TWEET_CREATED_AT));
            awaitFeedHolds(authorId, sentinelTweetId);

            assertThat(tweetIdsInFeedOf(authorId)).containsExactlyInAnyOrder(tweetId, sentinelTweetId);
            followers.forEach(follower ->
                    assertThat(tweetIdsInFeedOf(follower)).containsExactlyInAnyOrder(tweetId, sentinelTweetId));
        }
    }

    @Nested
    class Retries {

        @Test
        void should_retry_and_end_complete_without_duplicates_when_the_gateway_fails_then_recovers() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> firstPage = newUsers(2);
            List<UUID> secondPage = newUsers(2);
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .withQueryParam("cursor", absent())
                    .willReturn(followerPage(firstPage, "cursor-1")));
            String scenario = "flaky-" + authorId;
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .withQueryParam("cursor", equalTo("cursor-1"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("recovered"));
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .withQueryParam("cursor", equalTo("cursor-1"))
                    .inScenario(scenario)
                    .whenScenarioStateIs("recovered")
                    .willReturn(followerPage(secondPage, null)));

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT));

            secondPage.forEach(userId -> awaitFeedHolds(userId, tweetId));
            List<UUID> everyone = new ArrayList<>(List.of(authorId));
            everyone.addAll(firstPage);
            everyone.addAll(secondPage);
            everyone.forEach(userId -> assertThat(tweetIdsInFeedOf(userId)).containsExactly(tweetId));
            GATEWAY_STUB.verifyThat(moreThanOrExactly(2), getRequestedFor(followerIdsPath(authorId))
                    .withQueryParam("cursor", absent()));
        }

        @Test
        void should_dead_letter_the_record_when_the_gateway_stays_down_past_the_retries() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            GATEWAY_STUB.register(get(followerIdsPath(authorId)).willReturn(aResponse().withStatus(503)));

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT));

            awaitDltRecordForKey(TWEET_CREATED_DLT_TOPIC, tweetId.toString());
            GATEWAY_STUB.verifyThat(moreThanOrExactly(2), getRequestedFor(followerIdsPath(authorId)));
        }
    }

    @Nested
    class InvalidEvents {

        @Test
        void should_dead_letter_without_retry_or_a_gateway_call_when_the_event_is_invalid() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            String withoutCreatedAt = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"tweetId\":\"" + tweetId
                    + "\",\"authorId\":\"" + authorId + "\",\"content\":\"hello\",\"imageIds\":[]}";
            UUID sentinelTweetId = TestIds.tweetId();
            UUID sentinelAuthorId = TestIds.userId();
            stubFollowerPages(sentinelAuthorId, List.of(List.of()));

            publish(TWEET_CREATED_TOPIC, tweetId.toString(), withoutCreatedAt);
            publish(TWEET_CREATED_TOPIC, tweetId.toString(),
                    tweetCreatedJson(sentinelTweetId, sentinelAuthorId, TWEET_CREATED_AT));
            awaitFeedHolds(sentinelAuthorId, sentinelTweetId);

            awaitDltRecordForKey(TWEET_CREATED_DLT_TOPIC, tweetId.toString());
            verify(feedFanOutService, times(2)).fanOut(any(TweetCreatedEventDTO.class));
            GATEWAY_STUB.verifyThat(0, getRequestedFor(followerIdsPath(authorId)));
            assertThat(tweetIdsInFeedOf(authorId)).isEmpty();
        }
    }
}
