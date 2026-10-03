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
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

/**
 * Every scenario releases all its threads from one latch, so the calls really overlap, and asserts only the
 * final state, never an interleaving. The services are called directly: the listeners add nothing but the
 * hand-over, and Kafka's per-key ordering would hide the overlap this suite is about.
 */
class FeedConcurrencyIntegrationTest extends AbstractListenerIntegrationTest {

    private static final int PARALLEL_FAN_OUTS = 8;

    private static final int RACE_ROUNDS = 20;

    private static final int FOLLOWERS_PER_PAGE = 4;

    @Nested
    class SameEventTwice {

        @Test
        void should_give_the_author_and_every_follower_one_entry_when_the_same_event_is_processed_in_parallel() throws Exception {
            Author author = newAuthorWithFollowerPages(3);
            TweetCreatedEventDTO event = tweetCreated(author);

            List<Callable<Void>> fanOuts = IntStream
                    .range(0, PARALLEL_FAN_OUTS)
                    .<Callable<Void>>mapToObj(i -> () -> fanOut(event))
                    .toList();
            runTogether(fanOuts);

            for (UUID reader : author.readers()) {
                assertThat(tweetIdsInFeedOf(reader)).containsExactly(event.getTweetId());
            }
        }
    }

    @Nested
    class FanOutRacingDelete {

        @Test
        void should_never_fail_and_keep_the_entries_to_the_right_readers_and_lose_them_to_a_repeated_delete_when_a_delete_races_the_fan_out() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Author author = newAuthorWithFollowerPages(3);
                TweetCreatedEventDTO created = tweetCreated(author);

                runTogether(List.of(() -> fanOut(created), () -> delete(created)));

                for (UUID reader : author.readers()) {
                    assertThat(tweetIdsInFeedOf(reader)).isSubsetOf(created.getTweetId());
                }
                for (UUID stranger : newUsers(3)) {
                    assertThat(tweetIdsInFeedOf(stranger)).isEmpty();
                }

                delete(created);

                for (UUID reader : author.readers()) {
                    assertThat(tweetIdsInFeedOf(reader)).isEmpty();
                }
            }
        }
    }

    @Nested
    class FanOutRacingUnfollow {

        @Test
        void should_keep_a_tweet_posted_after_the_unfollow_and_drop_the_older_one_when_an_unfollow_races_the_fan_out() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Author author = newAuthorWithFollowerPages(3);
                UUID unfollower = author.getFollowers().getFirst();
                UUID olderTweetId = TestIds.tweetId();
                seedEntry(unfollower, olderTweetId, author.getId(), TWEET_CREATED_AT.minusSeconds(60));
                TweetCreatedEventDTO newer = tweetCreated(author);

                runTogether(List.of(() -> fanOut(newer), () -> unfollow(unfollower, author.getId(), TWEET_CREATED_AT.minusSeconds(1))));

                assertThat(tweetIdsInFeedOf(unfollower)).containsExactly(newer.getTweetId());
                for (UUID reader : author.readers()) {
                    assertThat(tweetIdsInFeedOf(reader)).contains(newer.getTweetId());
                }
            }
        }

        @Test
        void should_never_fail_and_leave_the_other_readers_alone_when_an_unfollow_after_the_tweet_races_the_fan_out() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Author author = newAuthorWithFollowerPages(3);
                UUID unfollower = author.getFollowers().getFirst();
                UUID olderTweetId = TestIds.tweetId();
                seedEntry(unfollower, olderTweetId, author.getId(), TWEET_CREATED_AT.minusSeconds(60));
                TweetCreatedEventDTO created = tweetCreated(author);

                runTogether(List.of(() -> fanOut(created), () -> unfollow(unfollower, author.getId(), TWEET_CREATED_AT.plusSeconds(1))));

                // The tweet may land after the unfollow cleaned up: the accepted race. The older entry never survives.
                assertThat(tweetIdsInFeedOf(unfollower)).isSubsetOf(created.getTweetId());
                for (UUID reader : author.readers()) {
                    if (!reader.equals(unfollower)) {
                        assertThat(tweetIdsInFeedOf(reader)).containsExactly(created.getTweetId());
                    }
                }

                unfollow(unfollower, author.getId(), TWEET_CREATED_AT.plusSeconds(1));

                assertThat(tweetIdsInFeedOf(unfollower)).isEmpty();
            }
        }
    }

    private Author newAuthorWithFollowerPages(int pageCount) {
        UUID authorId = TestIds.userId();
        List<List<UUID>> pages = IntStream
                .range(0, pageCount)
                .mapToObj(i -> newUsers(FOLLOWERS_PER_PAGE))
                .toList();
        stubFollowerPages(authorId, pages);

        return new Author(authorId, pages.stream().flatMap(List::stream).toList());
    }

    private TweetCreatedEventDTO tweetCreated(Author author) {
        return TweetCreatedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(TestIds.tweetId())
                .authorId(author.getId())
                .createdAt(TWEET_CREATED_AT)
                .build();
    }

    private Void fanOut(TweetCreatedEventDTO event) {
        feedFanOutService.fanOut(event);

        return null;
    }

    private Void delete(TweetCreatedEventDTO created) {
        feedEntryCleanupService.onTweetDeleted(TweetDeletedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(created.getTweetId())
                .build());

        return null;
    }

    private Void unfollow(UUID followerId, UUID followeeId, Instant occurredAt) {
        feedEntryCleanupService.onUserUnfollowed(UserUnfollowedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .followerId(followerId)
                .followeeId(followeeId)
                .occurredAt(occurredAt)
                .build());

        return null;
    }

    @Value
    private static class Author {

        private final UUID id;

        private final List<UUID> followers;

        List<UUID> readers() {
            List<UUID> readers = new ArrayList<>(followers);
            readers.add(id);

            return readers;
        }
    }
}
