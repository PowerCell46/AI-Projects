package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.Follow;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursorCodec;

@ExtendWith(MockitoExtension.class)
class FollowListServiceImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID VIEWER_ID = UUID.randomUUID();

    private FollowListServiceImpl followListService;

    private User target;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @BeforeEach
    void setUp() {
        followListService = new FollowListServiceImpl(userRepository, followRepository);
        target = userWithId();
        target.setEnabled(true);
        lenient().when(userRepository.findByUsernameNormalized("target")).thenReturn(Optional.of(target));
    }

    @Nested
    class GetFollowers {

        @Test
        void should_query_the_first_page_with_one_extra_row_when_there_is_no_cursor() {
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any())).thenReturn(List.of());

            followListService.getFollowers(VIEWER_ID, "Target", null, 20);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(followRepository).findFollowersFirstPage(eq(target.getId()), limit.capture());
            assertThat(limit.getValue().getPageSize()).isEqualTo(21);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_query_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorId = UUID.randomUUID();
            String cursor = FollowCursorCodec.encode(CREATED_AT, cursorId);
            when(followRepository.findFollowersAfter(eq(target.getId()), eq(CREATED_AT), eq(cursorId), any()))
                    .thenReturn(List.of());

            followListService.getFollowers(VIEWER_ID, "target", cursor, 5);

            verify(followRepository, never()).findFollowersFirstPage(any(), any());
        }

        @Test
        void should_return_the_followers_as_items_when_the_page_has_rows() {
            User follower = userWithId();
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any()))
                    .thenReturn(List.of(followBy(follower, target, CREATED_AT)));
            when(followRepository.findFollowedIds(eq(VIEWER_ID), any())).thenReturn(List.of());

            FollowListResponseDTO page = followListService.getFollowers(VIEWER_ID, "target", null, 20);

            assertThat(page.getItems()).hasSize(1);
            assertThat(page.getItems().getFirst().getId()).isEqualTo(follower.getId());
        }

        @Test
        void should_drop_the_extra_row_and_build_the_cursor_from_the_last_kept_row_when_more_rows_exist() {
            Follow first = followBy(userWithId(), target, CREATED_AT.plusSeconds(2));
            Follow second = followBy(userWithId(), target, CREATED_AT.plusSeconds(1));
            Follow extra = followBy(userWithId(), target, CREATED_AT);
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any()))
                    .thenReturn(List.of(first, second, extra));
            when(followRepository.findFollowedIds(eq(VIEWER_ID), any())).thenReturn(List.of());

            FollowListResponseDTO page = followListService.getFollowers(VIEWER_ID, "target", null, 2);

            assertThat(page.getItems()).hasSize(2);
            assertThat(page.getNextCursor()).isEqualTo(FollowCursorCodec.encode(second.getCreatedAt(), second.getId()));
        }

        @Test
        void should_return_a_null_cursor_when_the_rows_exactly_fill_the_page() {
            Follow first = followBy(userWithId(), target, CREATED_AT.plusSeconds(1));
            Follow second = followBy(userWithId(), target, CREATED_AT);
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any()))
                    .thenReturn(List.of(first, second));
            when(followRepository.findFollowedIds(eq(VIEWER_ID), any())).thenReturn(List.of());

            assertThat(followListService.getFollowers(VIEWER_ID, "target", null, 2).getNextCursor()).isNull();
        }

        @Test
        void should_mark_only_the_ids_the_batch_query_returns_when_the_viewer_follows_some() {
            User followed = userWithId();
            User notFollowed = userWithId();
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any()))
                    .thenReturn(List.of(followBy(followed, target, CREATED_AT), followBy(notFollowed, target, CREATED_AT)));
            when(followRepository.findFollowedIds(eq(VIEWER_ID), any())).thenReturn(List.of(followed.getId()));

            FollowListResponseDTO page = followListService.getFollowers(VIEWER_ID, "target", null, 20);

            assertThat(page.getItems().get(0).isFollowedByMe()).isTrue();
            assertThat(page.getItems().get(1).isFollowedByMe()).isFalse();
        }

        @Test
        void should_skip_the_batch_query_when_the_page_is_empty() {
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any())).thenReturn(List.of());

            FollowListResponseDTO page = followListService.getFollowers(VIEWER_ID, "target", null, 20);

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
            verify(followRepository, never()).findFollowedIds(any(), any());
        }
    }

    @Nested
    class GetFollowing {

        @Test
        void should_return_the_followed_users_as_items_when_the_page_has_rows() {
            User followed = userWithId();
            when(followRepository.findFollowingFirstPage(eq(target.getId()), any()))
                    .thenReturn(List.of(followBy(target, followed, CREATED_AT)));
            when(followRepository.findFollowedIds(eq(VIEWER_ID), any())).thenReturn(List.of());

            FollowListResponseDTO page = followListService.getFollowing(VIEWER_ID, "target", null, 20);

            assertThat(page.getItems().getFirst().getId()).isEqualTo(followed.getId());
        }

        @Test
        void should_query_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorId = UUID.randomUUID();
            String cursor = FollowCursorCodec.encode(CREATED_AT, cursorId);
            when(followRepository.findFollowingAfter(eq(target.getId()), eq(CREATED_AT), eq(cursorId), any()))
                    .thenReturn(List.of());

            followListService.getFollowing(VIEWER_ID, "target", cursor, 5);

            verify(followRepository, never()).findFollowingFirstPage(any(), any());
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 101, Integer.MAX_VALUE})
        void should_throw_before_any_query_when_the_size_is_out_of_range(int size) {
            assertThatThrownBy(() -> followListService.getFollowers(VIEWER_ID, "target", null, size))
                    .isInstanceOf(InvalidPageSizeException.class);
            verifyNoInteractions(followRepository);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 100})
        void should_accept_the_size_when_it_is_on_the_boundary(int size) {
            when(followRepository.findFollowersFirstPage(eq(target.getId()), any())).thenReturn(List.of());

            assertThat(followListService.getFollowers(VIEWER_ID, "target", null, size).getItems()).isEmpty();
        }

        @Test
        void should_throw_before_any_query_when_the_cursor_is_malformed() {
            assertThatThrownBy(() -> followListService.getFollowing(VIEWER_ID, "target", "garbage!", 20))
                    .isInstanceOf(InvalidCursorException.class);
            verifyNoInteractions(followRepository);
        }

        @Test
        void should_throw_not_found_when_the_user_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followListService.getFollowers(VIEWER_ID, "nobody", null, 20))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void should_throw_not_found_when_the_user_is_unconfirmed() {
            target.setEnabled(false);

            assertThatThrownBy(() -> followListService.getFollowing(VIEWER_ID, "target", null, 20))
                    .isInstanceOf(UserNotFoundException.class);
            verifyNoInteractions(followRepository);
        }
    }

    private User userWithId() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());

        return user;
    }

    private Follow followBy(User follower, User following, Instant createdAt) {
        return Follow.builder()
                .id(UUID.randomUUID())
                .follower(follower)
                .following(following)
                .createdAt(createdAt)
                .build();
    }
}
