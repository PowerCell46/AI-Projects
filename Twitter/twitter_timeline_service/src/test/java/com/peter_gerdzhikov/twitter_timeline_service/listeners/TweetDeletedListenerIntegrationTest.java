package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetDeletedListenerIntegrationTest extends AbstractListenerIntegrationTest {

    @Nested
    class Deletes {

        @Test
        void should_remove_every_entry_of_the_tweet_when_it_is_deleted() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> users = newUsers(3);
            users.forEach(userId -> seedEntry(userId, tweetId, authorId, TWEET_CREATED_AT));

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, authorId));

            users.forEach(userId -> awaitFeedLacks(userId, tweetId));
        }

        @Test
        void should_leave_other_tweets_untouched_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedEntry(userId, deletedTweetId, authorId, TWEET_CREATED_AT);
            seedEntry(userId, keptTweetId, authorId, TWEET_CREATED_AT.plusSeconds(1));

            publish(TWEET_DELETED_TOPIC, deletedTweetId.toString(), tweetDeletedJson(deletedTweetId, authorId));
            awaitFeedLacks(userId, deletedTweetId);

            assertThat(tweetIdsInFeedOf(userId)).containsExactly(keptTweetId);
        }

        @Test
        void should_do_nothing_when_the_same_delete_is_repeated() {
            UUID tweetId = TestIds.tweetId();
            UUID sentinelTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedEntry(userId, tweetId, authorId, TWEET_CREATED_AT);
            seedEntry(userId, sentinelTweetId, authorId, TWEET_CREATED_AT.plusSeconds(1));
            seedEntry(userId, keptTweetId, authorId, TWEET_CREATED_AT.plusSeconds(2));
            String delete = tweetDeletedJson(tweetId, authorId);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(sentinelTweetId, authorId));
            awaitFeedLacks(userId, sentinelTweetId);

            assertThat(tweetIdsInFeedOf(userId)).containsExactly(keptTweetId);
        }

        @Test
        void should_leave_orphan_entries_when_the_delete_arrives_before_the_create() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> followers = newUsers(2);
            stubFollowerPages(authorId, List.of(followers));

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, authorId));
            publish(TWEET_CREATED_TOPIC, tweetId.toString(), tweetCreatedJson(tweetId, authorId, TWEET_CREATED_AT));

            followers.forEach(userId -> awaitFeedHolds(userId, tweetId));
            awaitFeedHolds(authorId, tweetId);
        }
    }

    @Nested
    class SavedTweets {

        @Test
        void should_remove_the_saved_rows_of_the_tweet_across_users_when_it_is_deleted() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            List<UUID> users = newUsers(3);
            users.forEach(userId -> seedSavedTweet(userId, tweetId, authorId, TWEET_CREATED_AT));

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, authorId));

            users.forEach(userId -> awaitSavedLacks(userId, tweetId));
        }

        @Test
        void should_leave_the_saved_rows_of_other_tweets_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedSavedTweet(userId, deletedTweetId, authorId, TWEET_CREATED_AT);
            seedSavedTweet(userId, keptTweetId, authorId, TWEET_CREATED_AT.plusSeconds(1));

            publish(TWEET_DELETED_TOPIC, deletedTweetId.toString(), tweetDeletedJson(deletedTweetId, authorId));
            awaitSavedLacks(userId, deletedTweetId);

            assertThat(tweetIdsSavedBy(userId)).containsExactly(keptTweetId);
        }

        @Test
        void should_remove_the_feed_entries_and_the_saved_rows_together_when_a_tweet_is_deleted() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedEntry(userId, tweetId, authorId, TWEET_CREATED_AT);
            seedSavedTweet(userId, tweetId, authorId, TWEET_CREATED_AT);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, authorId));

            awaitFeedLacks(userId, tweetId);
            awaitSavedLacks(userId, tweetId);
        }

        @Test
        void should_do_nothing_when_the_same_delete_is_repeated_and_the_tweet_was_saved() {
            UUID tweetId = TestIds.tweetId();
            UUID sentinelTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedSavedTweet(userId, tweetId, authorId, TWEET_CREATED_AT);
            seedSavedTweet(userId, sentinelTweetId, authorId, TWEET_CREATED_AT.plusSeconds(1));
            seedSavedTweet(userId, keptTweetId, authorId, TWEET_CREATED_AT.plusSeconds(2));
            String delete = tweetDeletedJson(tweetId, authorId);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(sentinelTweetId, authorId));
            awaitSavedLacks(userId, sentinelTweetId);

            assertThat(tweetIdsSavedBy(userId)).containsExactly(keptTweetId);
        }
    }

    @Nested
    class Views {

        @Test
        void should_remove_the_view_rows_and_the_counter_of_the_tweet_when_it_is_deleted() {
            UUID tweetId = TestIds.tweetId();
            seedViews(tweetId, 3);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, TestIds.userId()));

            awaitViewsGone(tweetId);
        }

        @Test
        void should_leave_the_views_and_the_counter_of_other_tweets_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            seedViews(deletedTweetId, 2);
            seedViews(keptTweetId, 2);

            publish(TWEET_DELETED_TOPIC, deletedTweetId.toString(), tweetDeletedJson(deletedTweetId, TestIds.userId()));
            awaitViewsGone(deletedTweetId);

            assertThat(viewersOf(keptTweetId)).hasSize(2);
            assertThat(viewsOf(keptTweetId)).isEqualTo(2);
        }

        @Test
        void should_remove_the_feed_entries_the_saved_rows_and_the_views_together_when_a_tweet_is_deleted() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            seedEntry(userId, tweetId, authorId, TWEET_CREATED_AT);
            seedSavedTweet(userId, tweetId, authorId, TWEET_CREATED_AT);
            seedViews(tweetId, 2);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, authorId));

            awaitFeedLacks(userId, tweetId);
            awaitSavedLacks(userId, tweetId);
            awaitViewsGone(tweetId);
        }

        @Test
        void should_do_nothing_when_the_same_delete_is_repeated_and_the_tweet_had_views() {
            UUID tweetId = TestIds.tweetId();
            UUID sentinelTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            seedViews(tweetId, 2);
            seedViews(sentinelTweetId, 1);
            seedViews(keptTweetId, 2);
            String delete = tweetDeletedJson(tweetId, TestIds.userId());

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), delete);
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(sentinelTweetId, TestIds.userId()));
            awaitViewsGone(sentinelTweetId);

            awaitViewsGone(tweetId);
            assertThat(viewsOf(keptTweetId)).isEqualTo(2);
        }

        @Test
        void should_do_nothing_when_the_tweet_had_no_views() {
            UUID tweetId = TestIds.tweetId();
            UUID sentinelTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            seedViews(sentinelTweetId, 1);
            seedViews(keptTweetId, 2);

            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(tweetId, TestIds.userId()));
            publish(TWEET_DELETED_TOPIC, tweetId.toString(), tweetDeletedJson(sentinelTweetId, TestIds.userId()));
            awaitViewsGone(sentinelTweetId);

            assertThat(viewsOf(tweetId)).isZero();
            assertThat(viewsOf(keptTweetId)).isEqualTo(2);
        }
    }
}
