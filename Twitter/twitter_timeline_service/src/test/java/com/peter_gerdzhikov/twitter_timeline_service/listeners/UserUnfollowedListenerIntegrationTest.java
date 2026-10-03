package com.peter_gerdzhikov.twitter_timeline_service.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class UserUnfollowedListenerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final Instant UNFOLLOWED_AT = TWEET_CREATED_AT.plusSeconds(10);

    @Nested
    class Unfollows {

        @Test
        void should_remove_the_followers_entries_by_that_author_up_to_the_occurred_at_time_when_the_user_unfollows() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID early = TestIds.tweetId();
            UUID atTheMoment = TestIds.tweetId();
            seedEntry(followerId, early, authorId, UNFOLLOWED_AT.minusSeconds(5));
            seedEntry(followerId, atTheMoment, authorId, UNFOLLOWED_AT);

            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), userUnfollowedJson(followerId, authorId, UNFOLLOWED_AT));

            awaitFeedLacks(followerId, early);
            awaitFeedLacks(followerId, atTheMoment);
        }

        @Test
        void should_keep_the_entries_after_the_occurred_at_time_when_the_user_unfollows() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID before = TestIds.tweetId();
            UUID after = TestIds.tweetId();
            seedEntry(followerId, before, authorId, UNFOLLOWED_AT.minusSeconds(1));
            seedEntry(followerId, after, authorId, UNFOLLOWED_AT.plusSeconds(1));

            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), userUnfollowedJson(followerId, authorId, UNFOLLOWED_AT));
            awaitFeedLacks(followerId, before);

            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(after);
        }

        @Test
        void should_keep_other_authors_entries_when_the_user_unfollows() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID byTheAuthor = TestIds.tweetId();
            UUID byAnotherAuthor = TestIds.tweetId();
            seedEntry(followerId, byTheAuthor, authorId, UNFOLLOWED_AT.minusSeconds(1));
            seedEntry(followerId, byAnotherAuthor, TestIds.userId(), UNFOLLOWED_AT.minusSeconds(1));

            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), userUnfollowedJson(followerId, authorId, UNFOLLOWED_AT));
            awaitFeedLacks(followerId, byTheAuthor);

            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(byAnotherAuthor);
        }

        @Test
        void should_keep_other_users_entries_when_a_user_unfollows() {
            UUID followerId = TestIds.userId();
            UUID otherUserId = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID followersTweet = TestIds.tweetId();
            UUID otherUsersTweet = TestIds.tweetId();
            seedEntry(followerId, followersTweet, authorId, UNFOLLOWED_AT.minusSeconds(1));
            seedEntry(otherUserId, otherUsersTweet, authorId, UNFOLLOWED_AT.minusSeconds(1));

            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), userUnfollowedJson(followerId, authorId, UNFOLLOWED_AT));
            awaitFeedLacks(followerId, followersTweet);

            assertThat(tweetIdsInFeedOf(otherUserId)).containsExactly(otherUsersTweet);
        }
    }

    @Nested
    class InvalidEvents {

        @Test
        void should_dead_letter_the_record_when_the_event_is_invalid() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            seedEntry(followerId, tweetId, authorId, UNFOLLOWED_AT.minusSeconds(1));
            String withoutOccurredAt = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"followerId\":\"" + followerId
                    + "\",\"followeeId\":\"" + authorId + "\"}";
            UUID otherAuthorId = TestIds.userId();
            UUID sentinelTweetId = TestIds.tweetId();
            seedEntry(followerId, sentinelTweetId, otherAuthorId, UNFOLLOWED_AT.minusSeconds(1));

            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), withoutOccurredAt);
            publish(USER_UNFOLLOWED_TOPIC, followerId.toString(), userUnfollowedJson(followerId, otherAuthorId, UNFOLLOWED_AT));
            awaitFeedLacks(followerId, sentinelTweetId);

            awaitDltRecordForKey(USER_UNFOLLOWED_DLT_TOPIC, followerId.toString());
            verify(feedEntryCleanupService, times(2)).onUserUnfollowed(any(UserUnfollowedEventDTO.class));
            assertThat(tweetIdsInFeedOf(followerId)).containsExactly(tweetId);
        }
    }
}
