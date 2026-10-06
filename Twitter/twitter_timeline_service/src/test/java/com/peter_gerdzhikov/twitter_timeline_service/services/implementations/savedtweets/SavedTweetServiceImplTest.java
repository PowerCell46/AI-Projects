package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.savedtweets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.savedtweets.SavedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.savedtweets.SavedTweet;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.savedtweets.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

@ExtendWith(MockitoExtension.class)
class SavedTweetServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00.123456789Z");

    private static final Instant SAVED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID USER = TestIds.userId();

    private SavedTweetServiceImpl savedTweetService;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private SavedTweetRepository savedTweetRepository;

    @Mock
    private TweetItemAssemblyService tweetItemAssemblyService;

    @BeforeEach
    void setUp() {
        savedTweetService = new SavedTweetServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC), tweetLookupService, savedTweetRepository, tweetItemAssemblyService);
    }

    @Nested
    class Save {

        @Test
        void should_store_the_tweet_with_the_author_the_tweet_service_reports_and_the_time_cut_to_microseconds() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenReturn(Map.of(tweetId, tweet(tweetId, authorId)));

            savedTweetService.save(USER, tweetId);

            verify(savedTweetRepository).insertIfAbsent(USER, tweetId, authorId, SAVED_AT);
        }

        @Test
        void should_throw_tweet_not_found_and_store_nothing_when_the_tweet_does_not_exist() {
            UUID tweetId = TestIds.tweetId();
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenReturn(Map.of());

            assertThatThrownBy(() -> savedTweetService.save(USER, tweetId)).isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(savedTweetRepository);
        }

        @Test
        void should_throw_the_lookups_own_exception_and_store_nothing_when_the_tweet_service_fails() {
            UUID tweetId = TestIds.tweetId();
            UpstreamUnavailableException failure = new UpstreamUnavailableException(new IllegalStateException("down"));
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenThrow(failure);

            assertThatThrownBy(() -> savedTweetService.save(USER, tweetId)).isSameAs(failure);

            verifyNoInteractions(savedTweetRepository);
        }
    }

    @Nested
    class Unsave {

        @Test
        void should_remove_the_row_without_asking_the_tweet_service() {
            UUID tweetId = TestIds.tweetId();

            savedTweetService.unsave(USER, tweetId);

            verify(savedTweetRepository).deleteByUserAndTweet(USER, tweetId);
            verifyNoInteractions(tweetLookupService);
        }
    }

    @Nested
    class GetSavedTweets {

        @Test
        void should_read_one_row_more_than_the_page_from_the_start_when_there_is_no_cursor() {
            when(savedTweetRepository.findFirstPage(eq(USER), any())).thenReturn(List.of());

            savedTweetService.getSavedTweets(USER, null, 20);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(savedTweetRepository).findFirstPage(eq(USER), limit.capture());
            assertThat(limit.getValue().getPageSize()).isEqualTo(21);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_read_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorTweetId = TestIds.tweetId();
            String cursor = TimelineCursorCodec.encode(SAVED_AT, cursorTweetId);
            when(savedTweetRepository.findPageAfter(eq(USER), eq(SAVED_AT), eq(cursorTweetId), any())).thenReturn(List.of());

            savedTweetService.getSavedTweets(USER, cursor, 5);

            verify(savedTweetRepository, never()).findFirstPage(any(), any());
        }

        @Test
        void should_return_no_cursor_when_the_rows_exactly_fill_the_page() {
            when(savedTweetRepository.findFirstPage(eq(USER), any()))
                    .thenReturn(List.of(saved(SAVED_AT.plusSeconds(1)), saved(SAVED_AT)));

            assertThat(savedTweetService.getSavedTweets(USER, null, 2).getNextCursor()).isNull();
        }

        @Test
        void should_drop_the_extra_row_and_build_the_cursor_from_the_last_kept_row_when_more_rows_exist() {
            SavedTweet first = saved(SAVED_AT.plusSeconds(2));
            SavedTweet second = saved(SAVED_AT.plusSeconds(1));
            SavedTweet extra = saved(SAVED_AT);
            when(savedTweetRepository.findFirstPage(eq(USER), any())).thenReturn(List.of(first, second, extra));

            SavedTweetsResponseDTO page = savedTweetService.getSavedTweets(USER, null, 2);

            assertThat(page.getNextCursor()).isEqualTo(TimelineCursorCodec.encode(second.getSavedAt(), second.getTweetId()));
            verify(tweetItemAssemblyService).assemble(eq(USER), eq(List.of(first, second)), any(), any());
        }

        @Test
        void should_return_the_assembled_items() {
            List<TweetItemResponseDTO> items = List.of(TweetItemResponseDTO.builder().id(TestIds.tweetId()).build());
            when(savedTweetRepository.findFirstPage(eq(USER), any())).thenReturn(List.of(saved(SAVED_AT)));
            when(tweetItemAssemblyService.assemble(eq(USER), any(), any(), any())).thenReturn(items);

            assertThat(savedTweetService.getSavedTweets(USER, null, 20).getItems()).isSameAs(items);
        }

        @Test
        void should_throw_invalid_page_size_and_read_nothing_when_the_size_is_out_of_range() {
            assertThatThrownBy(() -> savedTweetService.getSavedTweets(USER, null, 0)).isInstanceOf(InvalidPageSizeException.class);
            assertThatThrownBy(() -> savedTweetService.getSavedTweets(USER, null, 101)).isInstanceOf(InvalidPageSizeException.class);

            verifyNoInteractions(savedTweetRepository, tweetItemAssemblyService);
        }

        @Test
        void should_throw_invalid_cursor_and_read_nothing_when_the_cursor_is_malformed() {
            assertThatThrownBy(() -> savedTweetService.getSavedTweets(USER, "not-a-cursor", 20))
                    .isInstanceOf(InvalidCursorException.class);

            verifyNoInteractions(savedTweetRepository, tweetItemAssemblyService);
        }
    }

    private SavedTweet saved(Instant savedAt) {
        return SavedTweet
                .builder()
                .ownerId(USER)
                .tweetId(TestIds.tweetId())
                .authorId(TestIds.userId())
                .savedAt(savedAt)
                .build();
    }

    private TweetClientDTO tweet(UUID tweetId, UUID authorId) {
        return TweetClientDTO
                .builder()
                .id(tweetId)
                .authorId(authorId)
                .content("hello")
                .images(List.of())
                .build();
    }
}
