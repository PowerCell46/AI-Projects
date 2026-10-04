package com.peter_gerdzhikov.twitter_timeline_service.concurrency;

import static com.peter_gerdzhikov.twitter_timeline_service.support.LatchedTasks.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.Value;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

/**
 * Every scenario releases all its threads from one latch, so the calls really overlap, and asserts only the
 * final state, never an interleaving. The services are called directly: Kafka's per-key ordering would hide
 * the overlap this suite is about, because the follow and the unfollow of one pair are on different topics.
 */
class FeedBackfillConcurrencyIntegrationTest extends AbstractListenerIntegrationTest {

    private static final int RACE_ROUNDS = 20;

    private static final int TWEETS_PER_AUTHOR = 5;

    private static final int PARALLEL_BACKFILLS = 6;

    private static final Instant FOLLOWED_AT = Instant.parse("2026-01-05T00:00:00.123Z");

    @Nested
    class SameEventTwice {

        @Test
        void should_give_the_follower_each_tweet_once_when_the_same_event_is_processed_in_parallel() throws Exception {
            Pair pair = newPairWhoseFollowExists();
            UserFollowedEventDTO event = followed(pair);

            runTogether(backfills(event, PARALLEL_BACKFILLS));

            assertThat(tweetIdsInFeedOf(pair.getFollowerId())).containsExactlyInAnyOrderElementsOf(pair.tweetIds());
        }
    }

    @Nested
    class BackfillRacingFanOut {

        @Test
        void should_give_the_follower_each_tweet_once_when_a_fan_out_of_one_of_them_races_the_backfill() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Pair pair = newPairWhoseFollowExists();
                stubFollowerPages(pair.getFolloweeId(), List.of(List.of(pair.getFollowerId())));
                UserFollowedEventDTO event = followed(pair);
                AuthorTweet racing = pair.getTweets().getFirst();

                runTogether(List.of(() -> backfill(event), () -> fanOut(pair, racing)));

                assertThat(tweetIdsInFeedOf(pair.getFollowerId())).containsExactlyInAnyOrderElementsOf(pair.tweetIds());
            }
        }
    }

    @Nested
    class StormEndingUnfollowed {

        @Test
        void should_leave_none_of_the_followees_entries_when_a_follow_storm_ends_with_the_follow_gone() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Pair pair = newPair();
                stubFollowMissing(pair.getFollowerId(), pair.getFolloweeId());
                UserFollowedEventDTO event = followed(pair);
                List<Callable<Void>> storm = new ArrayList<>(backfills(event, PARALLEL_BACKFILLS));
                storm.add(() -> unfollow(pair));

                runTogether(storm);

                assertThat(tweetIdsInFeedOf(pair.getFollowerId())).isEmpty();
            }
        }

        @Test
        void should_keep_other_authors_entries_when_a_follow_storm_ends_with_the_follow_gone() throws Exception {
            Pair pair = newPair();
            stubFollowMissing(pair.getFollowerId(), pair.getFolloweeId());
            UUID byAnotherAuthor = TestIds.tweetId();
            seedEntry(pair.getFollowerId(), byAnotherAuthor, TestIds.userId(), FOLLOWED_AT.minusSeconds(60));

            runTogether(backfills(followed(pair), PARALLEL_BACKFILLS));

            assertThat(tweetIdsInFeedOf(pair.getFollowerId())).containsExactly(byAnotherAuthor);
        }
    }

    @Nested
    class StormEndingFollowed {

        @Test
        void should_leave_only_the_followees_real_tweets_once_each_when_a_follow_storm_ends_with_the_follow_in_place() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Pair pair = newPairWhoseFollowExists();
                UUID byAnotherAuthor = TestIds.tweetId();
                seedEntry(pair.getFollowerId(), byAnotherAuthor, TestIds.userId(), FOLLOWED_AT.minusSeconds(60));

                runTogether(backfills(followed(pair), PARALLEL_BACKFILLS));

                List<UUID> expected = new ArrayList<>(pair.tweetIds());
                expected.add(byAnotherAuthor);
                assertThat(tweetIdsInFeedOf(pair.getFollowerId())).containsExactlyInAnyOrderElementsOf(expected);
            }
        }
    }

    private Pair newPair() {
        UUID followeeId = TestIds.userId();
        List<AuthorTweet> tweets = IntStream
                .range(0, TWEETS_PER_AUTHOR)
                .mapToObj(index -> new AuthorTweet(TestIds.tweetId(), FOLLOWED_AT.minusSeconds(60L * (index + 1))))
                .toList();
        stubTweetsByAuthor(followeeId, tweets);

        return new Pair(TestIds.userId(), followeeId, tweets);
    }

    private Pair newPairWhoseFollowExists() {
        Pair pair = newPair();
        stubFollowExists(pair.getFollowerId(), pair.getFolloweeId());

        return pair;
    }

    private UserFollowedEventDTO followed(Pair pair) {
        return UserFollowedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .followerId(pair.getFollowerId())
                .followeeId(pair.getFolloweeId())
                .occurredAt(FOLLOWED_AT)
                .build();
    }

    private List<Callable<Void>> backfills(UserFollowedEventDTO event, int count) {
        return IntStream
                .range(0, count)
                .<Callable<Void>>mapToObj(index -> () -> backfill(event))
                .toList();
    }

    private Void backfill(UserFollowedEventDTO event) {
        feedBackfillService.backfill(event);

        return null;
    }

    private Void fanOut(Pair pair, AuthorTweet tweet) {
        feedFanOutService.fanOut(TweetCreatedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(tweet.getId())
                .authorId(pair.getFolloweeId())
                .createdAt(tweet.getCreatedAt())
                .build());

        return null;
    }

    private Void unfollow(Pair pair) {
        feedEntryCleanupService.onUserUnfollowed(UserUnfollowedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .followerId(pair.getFollowerId())
                .followeeId(pair.getFolloweeId())
                .occurredAt(FOLLOWED_AT.plusSeconds(1))
                .build());

        return null;
    }

    @Value
    private static class Pair {

        private final UUID followerId;

        private final UUID followeeId;

        private final List<AuthorTweet> tweets;

        List<UUID> tweetIds() {
            return tweets
                    .stream()
                    .map(AuthorTweet::getId)
                    .toList();
        }
    }
}
