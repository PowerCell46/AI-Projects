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
}
