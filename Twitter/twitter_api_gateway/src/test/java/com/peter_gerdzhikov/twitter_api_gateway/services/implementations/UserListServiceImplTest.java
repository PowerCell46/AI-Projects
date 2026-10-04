package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursorCodec;

@ExtendWith(MockitoExtension.class)
class UserListServiceImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID CALLER_ID = UUID.randomUUID();

    private UserListServiceImpl userListService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @BeforeEach
    void setUp() {
        userListService = new UserListServiceImpl(userRepository, followRepository);
    }

    @Nested
    class ListUsers {

        @Test
        void should_check_the_size_before_the_cursor_when_both_are_invalid() {
            assertThatThrownBy(() -> userListService.listUsers(CALLER_ID, "not-a-cursor", 0))
                    .isInstanceOf(InvalidPageSizeException.class);

            verifyNoInteractions(userRepository, followRepository);
        }

        @Test
        void should_throw_an_invalid_cursor_exception_when_the_cursor_is_not_one_the_codec_produced() {
            assertThatThrownBy(() -> userListService.listUsers(CALLER_ID, "not-a-cursor", 20))
                    .isInstanceOf(InvalidCursorException.class);

            verifyNoInteractions(userRepository, followRepository);
        }

        @Test
        void should_read_the_first_page_when_no_cursor_is_given() {
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of());

            userListService.listUsers(CALLER_ID, null, 20);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findUserListFirstPage(eq(CALLER_ID), limit.capture());
            verify(userRepository, never()).findUserListAfter(any(), any(), any(), any());
            assertThat(limit.getValue().getPageSize()).isEqualTo(21);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_read_the_page_after_the_cursor_when_a_cursor_is_given() {
            UUID cursorId = UUID.randomUUID();
            String cursor = FollowCursorCodec.encode(CREATED_AT, cursorId);
            when(userRepository.findUserListAfter(eq(CALLER_ID), eq(CREATED_AT), eq(cursorId), any()))
                    .thenReturn(List.of());

            userListService.listUsers(CALLER_ID, cursor, 5);

            verify(userRepository, never()).findUserListFirstPage(any(), any());
        }

        @Test
        void should_pass_the_caller_id_to_the_repository() {
            User listed = userWithId(CREATED_AT);
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of(listed));
            when(followRepository.findFollowedIds(eq(CALLER_ID), any())).thenReturn(List.of());

            userListService.listUsers(CALLER_ID, null, 20);

            verify(userRepository).findUserListFirstPage(eq(CALLER_ID), any());
            verify(followRepository).findFollowedIds(eq(CALLER_ID), any());
        }

        @Test
        void should_slice_size_plus_one_rows_into_size_items_when_more_rows_exist() {
            User first = userWithId(CREATED_AT.plusSeconds(2));
            User second = userWithId(CREATED_AT.plusSeconds(1));
            User extra = userWithId(CREATED_AT);
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of(first, second, extra));
            when(followRepository.findFollowedIds(eq(CALLER_ID), any())).thenReturn(List.of(second.getId()));

            UserListResponseDTO page = userListService.listUsers(CALLER_ID, null, 2);

            assertThat(page.getItems())
                    .extracting(item -> item.getId(), item -> item.isFollowedByMe())
                    .containsExactly(
                            tuple(first.getId(), false),
                            tuple(second.getId(), true)
                    );
        }

        @Test
        void should_build_the_next_cursor_from_the_last_returned_row_when_more_rows_exist() {
            User first = userWithId(CREATED_AT.plusSeconds(2));
            User second = userWithId(CREATED_AT.plusSeconds(1));
            User extra = userWithId(CREATED_AT);
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of(first, second, extra));
            when(followRepository.findFollowedIds(eq(CALLER_ID), any())).thenReturn(List.of());

            UserListResponseDTO page = userListService.listUsers(CALLER_ID, null, 2);

            assertThat(page.getNextCursor()).isEqualTo(FollowCursorCodec.encode(second.getCreatedAt(), second.getId()));
        }

        @Test
        void should_return_a_null_next_cursor_when_the_rows_do_not_exceed_the_size() {
            User first = userWithId(CREATED_AT.plusSeconds(1));
            User second = userWithId(CREATED_AT);
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of(first, second));
            when(followRepository.findFollowedIds(eq(CALLER_ID), any())).thenReturn(List.of());

            assertThat(userListService.listUsers(CALLER_ID, null, 2).getNextCursor()).isNull();
        }

        @Test
        void should_skip_the_followed_by_me_query_when_the_page_is_empty() {
            when(userRepository.findUserListFirstPage(eq(CALLER_ID), any())).thenReturn(List.of());

            UserListResponseDTO page = userListService.listUsers(CALLER_ID, null, 20);

            assertThat(page.getItems()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
            verifyNoInteractions(followRepository);
        }
    }

    private User userWithId(Instant createdAt) {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(createdAt);

        return user;
    }
}
