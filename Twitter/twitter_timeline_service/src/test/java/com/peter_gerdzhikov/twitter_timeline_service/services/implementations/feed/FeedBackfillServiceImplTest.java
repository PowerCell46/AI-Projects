package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetSummaryClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class FeedBackfillServiceImplTest {

    private static final int BACKFILL_SIZE = 50;

    private static final Duration RETENTION = Duration.ofDays(7);

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");

    private static final Instant SINCE = Instant.parse("2026-01-03T00:00:00.123456789Z");

    private static final Instant OLDEST_CREATED_AT = Instant.parse("2026-01-05T00:00:00Z");

    private static final Instant FOLLOWED_AT = Instant.parse("2026-01-10T00:00:00.123456789Z");

    private static final Instant NEWEST_CREATED_AT = Instant.parse("2026-01-09T00:00:00.123456789Z");

    private static final Instant NEWEST_CREATED_AT_IN_MICROS = Instant.parse("2026-01-09T00:00:00.123456Z");

    private FeedBackfillServiceImpl feedBackfillService;

    private UserFollowedEventDTO event;

    @Mock
    private FeedEntryRepository feedEntryRepository;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private FollowLookupService followLookupService;

    @Mock
    private EventValidationService eventValidationService;

    @BeforeEach
    void setUp() {
        feedBackfillService = newService(BACKFILL_SIZE);
        event = UserFollowedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .followerId(TestIds.userId())
                .followeeId(TestIds.userId())
                .occurredAt(FOLLOWED_AT)
                .build();
    }

    @Nested
    class Construction {

        @Test
        void should_refuse_to_start_when_the_backfill_size_is_zero() {
            assertThatThrownBy(() -> newService(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.backfill-size must be positive");
        }

        @Test
        void should_refuse_to_start_when_the_backfill_size_is_negative() {
            assertThatThrownBy(() -> newService(-1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.backfill-size must be positive");
        }

        @Test
        void should_refuse_to_start_when_the_retention_is_not_positive() {
            assertThatThrownBy(() -> new FeedBackfillServiceImpl(
                    clock(), feedEntryRepository, tweetLookupService, followLookupService, eventValidationService,
                    Duration.ZERO, BACKFILL_SIZE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.retention must be positive");
        }
    }

    @Nested
    class Backfill {

        @Test
        void should_ask_for_the_newest_tweets_of_the_followee_since_the_retention_before_the_follow() {
            givenTweets(new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT));
            when(followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())).thenReturn(true);

            feedBackfillService.backfill(event);

            verify(tweetLookupService).findNewestByAuthor(event.getFolloweeId(), SINCE, BACKFILL_SIZE);
        }

        @Test
        void should_insert_each_tweet_into_the_followers_feed_with_the_time_cut_to_microseconds() {
            TweetSummaryClientDTO newest = new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT);
            TweetSummaryClientDTO oldest = new TweetSummaryClientDTO(TestIds.tweetId(), OLDEST_CREATED_AT);
            givenTweets(newest, oldest);
            when(followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())).thenReturn(true);

            feedBackfillService.backfill(event);

            verify(feedEntryRepository).insertIfAbsent(
                    new UUID[]{event.getFollowerId()}, newest.getId(), event.getFolloweeId(), NEWEST_CREATED_AT_IN_MICROS);
            verify(feedEntryRepository).insertIfAbsent(
                    new UUID[]{event.getFollowerId()}, oldest.getId(), event.getFolloweeId(), OLDEST_CREATED_AT);
        }

        @Test
        void should_check_the_follow_only_after_the_tweets_are_inserted() {
            TweetSummaryClientDTO tweet = new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT);
            givenTweets(tweet);
            when(followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())).thenReturn(true);

            feedBackfillService.backfill(event);

            InOrder order = inOrder(feedEntryRepository, followLookupService);
            order.verify(feedEntryRepository).insertIfAbsent(any(), eq(tweet.getId()), any(), any());
            order.verify(followLookupService).isFollowing(event.getFollowerId(), event.getFolloweeId());
        }

        @Test
        void should_keep_the_entries_when_the_follow_still_exists() {
            givenTweets(new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT));
            when(followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())).thenReturn(true);

            feedBackfillService.backfill(event);

            verify(feedEntryRepository, never()).deleteByUnfollow(any(), any(), any());
        }

        @Test
        void should_remove_the_followees_entries_up_to_now_when_the_follow_is_gone() {
            givenTweets(new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT));
            when(followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())).thenReturn(false);

            feedBackfillService.backfill(event);

            verify(feedEntryRepository).deleteByUnfollow(event.getFollowerId(), event.getFolloweeId(), NOW);
        }

        @Test
        void should_do_nothing_more_when_the_followee_has_no_recent_tweets() {
            givenTweets();

            feedBackfillService.backfill(event);

            verify(tweetLookupService).findNewestByAuthor(any(), any(), anyInt());
            verifyNoInteractions(feedEntryRepository, followLookupService);
        }

        @Test
        void should_insert_nothing_and_check_nothing_when_the_tweet_service_is_unavailable() {
            when(tweetLookupService.findNewestByAuthor(any(), any(), anyInt())).thenThrow(new UpstreamUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> feedBackfillService.backfill(event)).isInstanceOf(UpstreamUnavailableException.class);

            verifyNoInteractions(feedEntryRepository, followLookupService);
        }

        @Test
        void should_keep_the_inserted_entries_and_rethrow_when_the_gateway_is_unavailable_for_the_check() {
            givenTweets(new TweetSummaryClientDTO(TestIds.tweetId(), NEWEST_CREATED_AT));
            when(followLookupService.isFollowing(any(), any())).thenThrow(new UpstreamUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> feedBackfillService.backfill(event)).isInstanceOf(UpstreamUnavailableException.class);

            verify(feedEntryRepository, never()).deleteByUnfollow(any(), any(), any());
        }
    }

    @Nested
    class Validation {

        @Test
        void should_make_no_call_when_the_event_is_invalid() {
            doThrow(new InvalidEventException("Invalid user.followed event.")).when(eventValidationService).validate(any(), any());

            assertThatThrownBy(() -> feedBackfillService.backfill(event)).isInstanceOf(InvalidEventException.class);

            verifyNoInteractions(tweetLookupService, followLookupService, feedEntryRepository);
        }

        @Test
        void should_validate_the_event_before_anything_else() {
            givenTweets();

            feedBackfillService.backfill(event);

            InOrder order = inOrder(eventValidationService, tweetLookupService);
            order.verify(eventValidationService).validate(eq(event), any());
            order.verify(tweetLookupService).findNewestByAuthor(any(), any(), anyInt());
        }
    }

    private FeedBackfillServiceImpl newService(int backfillSize) {
        return new FeedBackfillServiceImpl(
                clock(), feedEntryRepository, tweetLookupService, followLookupService, eventValidationService,
                RETENTION, backfillSize);
    }

    private Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private void givenTweets(TweetSummaryClientDTO... tweets) {
        when(tweetLookupService.findNewestByAuthor(any(), any(), anyInt())).thenReturn(List.of(tweets));
    }
}
