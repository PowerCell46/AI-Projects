package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class FeedEntryCleanupServiceImplTest {

    private FeedEntryCleanupServiceImpl feedEntryCleanupService;

    @Mock
    private FeedEntryRepository feedEntryRepository;

    @Mock
    private SavedTweetRepository savedTweetRepository;

    @Mock
    private EventValidationService eventValidationService;

    @BeforeEach
    void setUp() {
        feedEntryCleanupService = new FeedEntryCleanupServiceImpl(feedEntryRepository, savedTweetRepository, eventValidationService);
    }

    @Nested
    class OnTweetDeleted {

        private final TweetDeletedEventDTO event = TweetDeletedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(TestIds.tweetId())
                .build();

        @Test
        void should_remove_the_tweet_from_every_feed() {
            feedEntryCleanupService.onTweetDeleted(event);

            verify(feedEntryRepository).deleteByTweetId(event.getTweetId());
        }

        @Test
        void should_remove_the_tweet_from_every_saved_list() {
            feedEntryCleanupService.onTweetDeleted(event);

            verify(savedTweetRepository).deleteByTweetId(event.getTweetId());
        }

        @Test
        void should_validate_the_event_before_removing_anything() {
            InvalidEventException invalid = new InvalidEventException("Invalid tweet.deleted event.");
            doThrow(invalid).when(eventValidationService).validate(eq(event), any());

            assertThatThrownBy(() -> feedEntryCleanupService.onTweetDeleted(event)).isSameAs(invalid);

            verifyNoInteractions(feedEntryRepository, savedTweetRepository);
        }
    }

    @Nested
    class OnUserUnfollowed {

        private final UserUnfollowedEventDTO event = UserUnfollowedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .followerId(TestIds.userId())
                .followeeId(TestIds.userId())
                .occurredAt(Instant.parse("2026-01-01T00:00:00.123456789Z"))
                .build();

        @Test
        void should_remove_the_followees_entries_up_to_the_moment_cut_to_microseconds() {
            feedEntryCleanupService.onUserUnfollowed(event);

            ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
            verify(feedEntryRepository).deleteByUnfollow(eq(event.getFollowerId()), eq(event.getFolloweeId()), bound.capture());
            assertThat(bound.getValue()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
        }

        @Test
        void should_validate_the_event_before_removing_anything() {
            InvalidEventException invalid = new InvalidEventException("Invalid user.unfollowed event.");
            doThrow(invalid).when(eventValidationService).validate(eq(event), any());

            assertThatThrownBy(() -> feedEntryCleanupService.onUserUnfollowed(event)).isSameAs(invalid);

            verifyNoInteractions(feedEntryRepository);
        }
    }
}
