package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.FeedResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.feed.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.implementations.TweetItemAssemblyServiceImpl;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

@ExtendWith(MockitoExtension.class)
class FeedServiceImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID OWNER = TestIds.userId();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private FeedServiceImpl feedService;

    @Mock
    private ViewService viewService;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private FeedEntryRepository feedEntryRepository;

    @Mock
    private SavedTweetRepository savedTweetRepository;

    @BeforeEach
    void setUp() {
        feedService = new FeedServiceImpl(
                feedEntryRepository,
                new TweetItemAssemblyServiceImpl(viewService, executor, userLookupService, tweetLookupService, savedTweetRepository));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Nested
    class Paging {

        @Test
        void should_read_one_row_more_than_the_page_from_the_start_when_there_is_no_cursor() {
            when(feedEntryRepository.findFirstPage(eq(OWNER), any())).thenReturn(List.of());

            feedService.getFeed(OWNER, null, 20);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(feedEntryRepository).findFirstPage(eq(OWNER), limit.capture());
            assertThat(limit.getValue().getPageSize()).isEqualTo(21);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_read_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorTweetId = TestIds.tweetId();
            String cursor = TimelineCursorCodec.encode(CREATED_AT, cursorTweetId);
            when(feedEntryRepository.findPageAfter(eq(OWNER), eq(CREATED_AT), eq(cursorTweetId), any())).thenReturn(List.of());

            feedService.getFeed(OWNER, cursor, 5);

            verify(feedEntryRepository, never()).findFirstPage(any(), any());
        }

        @Test
        void should_return_no_cursor_when_the_rows_exactly_fill_the_page() {
            FeedEntry first = entry(CREATED_AT.plusSeconds(1));
            FeedEntry second = entry(CREATED_AT);
            givenRows(first, second);
            givenEverythingExists(first, second);

            assertThat(feedService.getFeed(OWNER, null, 2).getNextCursor()).isNull();
        }

        @Test
        void should_drop_the_extra_row_and_build_the_cursor_from_the_last_kept_row_when_more_rows_exist() {
            FeedEntry first = entry(CREATED_AT.plusSeconds(2));
            FeedEntry second = entry(CREATED_AT.plusSeconds(1));
            FeedEntry extra = entry(CREATED_AT);
            givenRows(first, second, extra);
            givenEverythingExists(first, second);

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 2);

            assertThat(page.getItems()).hasSize(2);
            assertThat(page.getNextCursor()).isEqualTo(TimelineCursorCodec.encode(second.getTweetCreatedAt(), second.getTweetId()));
        }

        @Test
        void should_throw_invalid_page_size_and_read_nothing_when_the_size_is_out_of_range() {
            assertThatThrownBy(() -> feedService.getFeed(OWNER, null, 0)).isInstanceOf(InvalidPageSizeException.class);
            assertThatThrownBy(() -> feedService.getFeed(OWNER, null, 101)).isInstanceOf(InvalidPageSizeException.class);

            verifyNoInteractions(feedEntryRepository, tweetLookupService, userLookupService);
        }

        @Test
        void should_throw_invalid_cursor_and_read_nothing_when_the_cursor_is_malformed() {
            assertThatThrownBy(() -> feedService.getFeed(OWNER, "not-a-cursor", 20)).isInstanceOf(InvalidCursorException.class);

            verifyNoInteractions(feedEntryRepository, tweetLookupService, userLookupService);
        }
    }

    @Nested
    class Join {

        @Test
        void should_make_no_downstream_call_when_there_are_no_rows() {
            givenRows();

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 20);

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
            verifyNoInteractions(tweetLookupService, userLookupService, savedTweetRepository);
        }

        @Test
        void should_return_the_items_in_entry_order_with_their_authors() {
            FeedEntry newer = entry(CREATED_AT.plusSeconds(1));
            FeedEntry older = entry(CREATED_AT);
            givenRows(newer, older);
            givenEverythingExists(older, newer);

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 20);

            assertThat(page.getItems())
                    .extracting(item -> item.getId())
                    .containsExactly(newer.getTweetId(), older.getTweetId());
            assertThat(page.getItems().getFirst().getAuthor().getId()).isEqualTo(newer.getAuthorId());
        }

        @Test
        void should_put_the_view_count_of_each_tweet_on_its_item_and_zero_when_it_has_none() {
            FeedEntry viewed = entry(CREATED_AT.plusSeconds(1));
            FeedEntry unviewed = entry(CREATED_AT);
            givenRows(viewed, unviewed);
            givenEverythingExists(viewed, unviewed);
            when(viewService.countViews(any())).thenReturn(Map.of(viewed.getTweetId(), 7L));

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 20);

            assertThat(page.getItems())
                    .extracting(item -> item.getViews())
                    .containsExactly(7L, 0L);
        }

        @Test
        void should_count_views_once_for_the_tweets_of_the_page() {
            FeedEntry first = entry(CREATED_AT.plusSeconds(1));
            FeedEntry second = entry(CREATED_AT);
            givenRows(first, second);
            givenEverythingExists(first, second);

            feedService.getFeed(OWNER, null, 20);

            ArgumentCaptor<Collection<UUID>> tweetIds = ArgumentCaptor.captor();
            verify(viewService).countViews(tweetIds.capture());
            assertThat(tweetIds.getValue()).containsExactly(first.getTweetId(), second.getTweetId());
        }

        @Test
        void should_mark_each_item_saved_only_when_the_viewer_saved_its_tweet() {
            FeedEntry saved = entry(CREATED_AT.plusSeconds(1));
            FeedEntry unsaved = entry(CREATED_AT);
            givenRows(saved, unsaved);
            givenEverythingExists(saved, unsaved);
            when(savedTweetRepository.findSavedTweetIds(eq(OWNER), any())).thenReturn(List.of(saved.getTweetId()));

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 20);

            assertThat(page.getItems())
                    .extracting(item -> item.isSavedByMe())
                    .containsExactly(true, false);
        }

        @Test
        void should_look_up_the_saved_tweets_of_the_viewer_once_for_the_tweets_of_the_page() {
            FeedEntry first = entry(CREATED_AT.plusSeconds(1));
            FeedEntry second = entry(CREATED_AT);
            givenRows(first, second);
            givenEverythingExists(first, second);

            feedService.getFeed(OWNER, null, 20);

            ArgumentCaptor<Collection<UUID>> tweetIds = ArgumentCaptor.captor();
            verify(savedTweetRepository).findSavedTweetIds(eq(OWNER), tweetIds.capture());
            assertThat(tweetIds.getValue()).containsExactly(first.getTweetId(), second.getTweetId());
        }

        @Test
        void should_skip_an_entry_whose_tweet_is_missing_but_keep_the_cursor_moving() {
            FeedEntry kept = entry(CREATED_AT.plusSeconds(2));
            FeedEntry gone = entry(CREATED_AT.plusSeconds(1));
            FeedEntry next = entry(CREATED_AT);
            givenRows(kept, gone, next);
            givenEverythingExists(kept);
            when(userLookupService.findByIds(any())).thenReturn(authorsOf(kept, gone));

            FeedResponseDTO page = feedService.getFeed(OWNER, null, 2);

            assertThat(page.getItems()).extracting(item -> item.getId()).containsExactly(kept.getTweetId());
            assertThat(page.getNextCursor()).isEqualTo(TimelineCursorCodec.encode(gone.getTweetCreatedAt(), gone.getTweetId()));
        }

        @Test
        void should_skip_an_entry_whose_author_is_missing() {
            FeedEntry kept = entry(CREATED_AT.plusSeconds(1));
            FeedEntry orphaned = entry(CREATED_AT);
            givenRows(kept, orphaned);
            when(tweetLookupService.findByIds(any())).thenReturn(tweetsOf(kept, orphaned));
            when(userLookupService.findByIds(any())).thenReturn(authorsOf(kept));

            assertThat(feedService.getFeed(OWNER, null, 20).getItems())
                    .extracting(item -> item.getId())
                    .containsExactly(kept.getTweetId());
        }

        @Test
        void should_ask_for_each_tweet_and_each_distinct_author_of_the_page_once() {
            UUID sameAuthor = TestIds.userId();
            FeedEntry first = entryBy(sameAuthor, CREATED_AT.plusSeconds(1));
            FeedEntry second = entryBy(sameAuthor, CREATED_AT);
            givenRows(first, second);
            givenEverythingExists(first, second);

            feedService.getFeed(OWNER, null, 20);

            ArgumentCaptor<Collection<UUID>> tweetIds = ArgumentCaptor.captor();
            ArgumentCaptor<Collection<UUID>> authorIds = ArgumentCaptor.captor();
            verify(tweetLookupService).findByIds(tweetIds.capture());
            verify(userLookupService).findByIds(authorIds.capture());
            assertThat(tweetIds.getValue()).containsExactly(first.getTweetId(), second.getTweetId());
            assertThat(authorIds.getValue()).containsExactly(sameAuthor);
        }

        @Test
        void should_call_the_tweet_service_and_the_gateway_at_the_same_time() {
            FeedEntry entry = entry(CREATED_AT);
            givenRows(entry);
            CountDownLatch bothStarted = new CountDownLatch(2);
            when(tweetLookupService.findByIds(any())).thenAnswer(invocation -> {
                awaitTheOtherCall(bothStarted);

                return tweetsOf(entry);
            });
            when(userLookupService.findByIds(any())).thenAnswer(invocation -> {
                awaitTheOtherCall(bothStarted);

                return authorsOf(entry);
            });

            assertThat(feedService.getFeed(OWNER, null, 20).getItems()).hasSize(1);
        }
    }

    @Nested
    class Failures {

        @Test
        void should_throw_the_tweet_lookups_own_exception_when_it_fails() {
            FeedEntry entry = entry(CREATED_AT);
            givenRows(entry);
            UpstreamUnavailableException failure = new UpstreamUnavailableException(new IllegalStateException("down"));
            when(tweetLookupService.findByIds(any())).thenThrow(failure);
            when(userLookupService.findByIds(any())).thenReturn(authorsOf(entry));

            assertThatThrownBy(() -> feedService.getFeed(OWNER, null, 20)).isSameAs(failure);
        }

        @Test
        void should_throw_the_user_lookups_own_exception_when_it_fails() {
            FeedEntry entry = entry(CREATED_AT);
            givenRows(entry);
            UpstreamTimeoutException failure = new UpstreamTimeoutException(new IllegalStateException("slow"));
            when(tweetLookupService.findByIds(any())).thenReturn(tweetsOf(entry));
            when(userLookupService.findByIds(any())).thenThrow(failure);

            assertThatThrownBy(() -> feedService.getFeed(OWNER, null, 20)).isSameAs(failure);
        }
    }

    private void awaitTheOtherCall(CountDownLatch bothStarted) throws InterruptedException {
        bothStarted.countDown();
        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).as("both calls were in flight together").isTrue();
    }

    private FeedEntry entry(Instant tweetCreatedAt) {
        return entryBy(TestIds.userId(), tweetCreatedAt);
    }

    private FeedEntry entryBy(UUID authorId, Instant tweetCreatedAt) {
        return FeedEntry
                .builder()
                .ownerId(OWNER)
                .tweetId(TestIds.tweetId())
                .authorId(authorId)
                .tweetCreatedAt(tweetCreatedAt)
                .build();
    }

    private void givenRows(FeedEntry... rows) {
        when(feedEntryRepository.findFirstPage(eq(OWNER), any())).thenReturn(List.of(rows));
    }

    private void givenEverythingExists(FeedEntry... entries) {
        when(tweetLookupService.findByIds(any())).thenReturn(tweetsOf(entries));
        when(userLookupService.findByIds(any())).thenReturn(authorsOf(entries));
    }

    private Map<UUID, TweetClientDTO> tweetsOf(FeedEntry... entries) {
        return Arrays
                .stream(entries)
                .collect(Collectors.toMap(
                        FeedEntry::getTweetId,
                        entry -> TweetClientDTO
                                .builder()
                                .id(entry.getTweetId())
                                .authorId(entry.getAuthorId())
                                .content("hello")
                                .createdAt(entry.getTweetCreatedAt())
                                .updatedAt(entry.getTweetCreatedAt())
                                .images(List.of())
                                .build()));
    }

    private Map<UUID, UserClientDTO> authorsOf(FeedEntry... entries) {
        return Arrays
                .stream(entries)
                .map(FeedEntry::getAuthorId)
                .distinct()
                .collect(Collectors.toMap(
                        authorId -> authorId,
                        authorId -> new UserClientDTO(authorId, "user", null)));
    }
}
