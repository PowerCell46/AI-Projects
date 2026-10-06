package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.likes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.likes.LikedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLike;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes.LikeRecordingService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00.123456789Z");

    private static final Instant LIKED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID USER = TestIds.userId();

    private LikeServiceImpl likeService;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private TweetLikeRepository tweetLikeRepository;

    @Mock
    private LikeRecordingService likeRecordingService;

    @Mock
    private TweetItemAssemblyService tweetItemAssemblyService;

    @BeforeEach
    void setUp() {
        likeService = new LikeServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC), tweetLookupService, tweetLikeRepository, likeRecordingService,
                tweetItemAssemblyService);
    }

    @Nested
    class Like {

        @Test
        void should_record_the_like_with_the_author_the_tweet_service_reports_and_the_time_cut_to_microseconds() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenReturn(Map.of(tweetId, tweet(tweetId, authorId)));

            likeService.like(USER, tweetId);

            verify(likeRecordingService).like(USER, tweetId, authorId, LIKED_AT);
        }

        @Test
        void should_throw_tweet_not_found_and_record_nothing_when_the_tweet_does_not_exist() {
            UUID tweetId = TestIds.tweetId();
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenReturn(Map.of());

            assertThatThrownBy(() -> likeService.like(USER, tweetId)).isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(likeRecordingService);
        }

        @Test
        void should_throw_the_lookups_own_exception_and_record_nothing_when_the_tweet_service_fails() {
            UUID tweetId = TestIds.tweetId();
            UpstreamUnavailableException failure = new UpstreamUnavailableException(new IllegalStateException("down"));
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenThrow(failure);

            assertThatThrownBy(() -> likeService.like(USER, tweetId)).isSameAs(failure);

            verifyNoInteractions(likeRecordingService);
        }

        @Test
        void should_ask_the_tweet_service_before_recording_the_like() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            when(tweetLookupService.findByIds(Set.of(tweetId))).thenReturn(Map.of(tweetId, tweet(tweetId, authorId)));

            likeService.like(USER, tweetId);

            InOrder order = inOrder(tweetLookupService, likeRecordingService);
            order.verify(tweetLookupService).findByIds(Set.of(tweetId));
            order.verify(likeRecordingService).like(USER, tweetId, authorId, LIKED_AT);
        }
    }

    @Nested
    class Unlike {

        @Test
        void should_remove_the_like_without_asking_the_tweet_service() {
            UUID tweetId = TestIds.tweetId();

            likeService.unlike(USER, tweetId);

            verify(likeRecordingService).unlike(USER, tweetId);
            verifyNoInteractions(tweetLookupService);
        }
    }

    @Nested
    class GetLikedTweets {

        @Test
        void should_read_one_row_more_than_the_page_from_the_start_when_there_is_no_cursor() {
            when(tweetLikeRepository.findFirstPage(eq(USER), any())).thenReturn(List.of());

            likeService.getLikedTweets(USER, null, 20);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(tweetLikeRepository).findFirstPage(eq(USER), limit.capture());
            assertThat(limit.getValue().getPageSize()).isEqualTo(21);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_read_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorTweetId = TestIds.tweetId();
            String cursor = TimelineCursorCodec.encode(LIKED_AT, cursorTweetId);
            when(tweetLikeRepository.findPageAfter(eq(USER), eq(LIKED_AT), eq(cursorTweetId), any())).thenReturn(List.of());

            likeService.getLikedTweets(USER, cursor, 5);

            verify(tweetLikeRepository, never()).findFirstPage(any(), any());
        }

        @Test
        void should_return_no_cursor_when_the_rows_exactly_fill_the_page() {
            when(tweetLikeRepository.findFirstPage(eq(USER), any()))
                    .thenReturn(List.of(liked(LIKED_AT.plusSeconds(1)), liked(LIKED_AT)));

            assertThat(likeService.getLikedTweets(USER, null, 2).getNextCursor()).isNull();
        }

        @Test
        void should_drop_the_extra_row_and_build_the_cursor_from_the_last_kept_row_when_more_rows_exist() {
            TweetLike first = liked(LIKED_AT.plusSeconds(2));
            TweetLike second = liked(LIKED_AT.plusSeconds(1));
            TweetLike extra = liked(LIKED_AT);
            when(tweetLikeRepository.findFirstPage(eq(USER), any())).thenReturn(List.of(first, second, extra));

            LikedTweetsResponseDTO page = likeService.getLikedTweets(USER, null, 2);

            assertThat(page.getNextCursor()).isEqualTo(TimelineCursorCodec.encode(second.getLikedAt(), second.getTweetId()));
            verify(tweetItemAssemblyService).assemble(eq(USER), eq(List.of(first, second)), any(), any());
        }

        @Test
        void should_return_the_assembled_items() {
            List<TweetItemResponseDTO> items = List.of(TweetItemResponseDTO.builder().id(TestIds.tweetId()).build());
            when(tweetLikeRepository.findFirstPage(eq(USER), any())).thenReturn(List.of(liked(LIKED_AT)));
            when(tweetItemAssemblyService.assemble(eq(USER), any(), any(), any())).thenReturn(items);

            assertThat(likeService.getLikedTweets(USER, null, 20).getItems()).isSameAs(items);
        }

        @Test
        void should_throw_invalid_page_size_and_read_nothing_when_the_size_is_out_of_range() {
            assertThatThrownBy(() -> likeService.getLikedTweets(USER, null, 0)).isInstanceOf(InvalidPageSizeException.class);
            assertThatThrownBy(() -> likeService.getLikedTweets(USER, null, 101)).isInstanceOf(InvalidPageSizeException.class);

            verifyNoInteractions(tweetLikeRepository, tweetItemAssemblyService);
        }

        @Test
        void should_throw_invalid_cursor_and_read_nothing_when_the_cursor_is_malformed() {
            assertThatThrownBy(() -> likeService.getLikedTweets(USER, "not-a-cursor", 20))
                    .isInstanceOf(InvalidCursorException.class);

            verifyNoInteractions(tweetLikeRepository, tweetItemAssemblyService);
        }
    }

    private TweetLike liked(Instant likedAt) {
        return TweetLike
                .builder()
                .ownerId(USER)
                .tweetId(TestIds.tweetId())
                .authorId(TestIds.userId())
                .likedAt(likedAt)
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
