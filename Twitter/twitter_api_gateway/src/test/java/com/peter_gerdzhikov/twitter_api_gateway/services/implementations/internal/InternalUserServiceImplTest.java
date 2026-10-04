package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.internal;

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
import java.util.stream.Stream;

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

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.FollowerIdsResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserIdsOutOfRangeException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.projections.FollowerEdge;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.follows.FollowCursorCodec;

import lombok.AllArgsConstructor;
import lombok.Getter;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID USER_ID = UUID.randomUUID();

    private InternalUserServiceImpl internalUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @BeforeEach
    void setUp() {
        internalUserService = new InternalUserServiceImpl(userRepository, followRepository);
    }

    @Nested
    class GetFollowerIds {

        @Test
        void should_query_the_first_page_with_one_extra_row_when_there_is_no_cursor() {
            when(followRepository.findFollowerEdgesFirstPage(eq(USER_ID), any())).thenReturn(List.of());

            internalUserService.getFollowerIds(USER_ID, null, 1000);

            ArgumentCaptor<Pageable> limit = ArgumentCaptor.forClass(Pageable.class);
            verify(followRepository).findFollowerEdgesFirstPage(eq(USER_ID), limit.capture());
            assertThat(limit.getValue().getPageSize()).isEqualTo(1001);
            assertThat(limit.getValue().getPageNumber()).isZero();
        }

        @Test
        void should_query_after_the_cursor_position_when_a_cursor_is_given() {
            UUID cursorId = UUID.randomUUID();
            String cursor = FollowCursorCodec.encode(CREATED_AT, cursorId);
            when(followRepository.findFollowerEdgesAfter(eq(USER_ID), eq(CREATED_AT), eq(cursorId), any()))
                    .thenReturn(List.of());

            internalUserService.getFollowerIds(USER_ID, cursor, 5);

            verify(followRepository, never()).findFollowerEdgesFirstPage(any(), any());
        }

        @Test
        void should_return_the_follower_ids_in_row_order_and_no_cursor_when_the_rows_fit_the_page() {
            FollowerEdge first = edge(CREATED_AT.plusSeconds(1));
            FollowerEdge second = edge(CREATED_AT);
            when(followRepository.findFollowerEdgesFirstPage(eq(USER_ID), any())).thenReturn(List.of(first, second));

            FollowerIdsResponseDTO page = internalUserService.getFollowerIds(USER_ID, null, 2);

            assertThat(page.getIds()).containsExactly(first.getFollowerId(), second.getFollowerId());
            assertThat(page.getNextCursor()).isNull();
        }

        @Test
        void should_drop_the_extra_row_and_build_the_cursor_from_the_last_kept_follow_when_more_rows_exist() {
            FollowerEdge first = edge(CREATED_AT.plusSeconds(2));
            FollowerEdge second = edge(CREATED_AT.plusSeconds(1));
            FollowerEdge extra = edge(CREATED_AT);
            when(followRepository.findFollowerEdgesFirstPage(eq(USER_ID), any()))
                    .thenReturn(List.of(first, second, extra));

            FollowerIdsResponseDTO page = internalUserService.getFollowerIds(USER_ID, null, 2);

            assertThat(page.getIds()).containsExactly(first.getFollowerId(), second.getFollowerId());
            assertThat(page.getNextCursor())
                    .isEqualTo(FollowCursorCodec.encode(second.getCreatedAt(), second.getFollowId()));
        }

        @Test
        void should_return_an_empty_page_when_the_user_has_no_followers() {
            when(followRepository.findFollowerEdgesFirstPage(eq(USER_ID), any())).thenReturn(List.of());

            FollowerIdsResponseDTO page = internalUserService.getFollowerIds(USER_ID, null, 20);

            assertThat(page.getIds()).isEmpty();
            assertThat(page.getNextCursor()).isNull();
        }

        @ParameterizedTest
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1001, Integer.MAX_VALUE})
        void should_throw_invalid_page_size_and_not_query_when_the_size_is_out_of_range(int size) {
            assertThatThrownBy(() -> internalUserService.getFollowerIds(USER_ID, null, size))
                    .isInstanceOf(InvalidPageSizeException.class)
                    .hasMessage("Page size must be between 1 and 1000.");
            verifyNoInteractions(followRepository);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 1000})
        void should_accept_the_size_when_it_is_on_the_boundary(int size) {
            when(followRepository.findFollowerEdgesFirstPage(eq(USER_ID), any())).thenReturn(List.of());

            assertThat(internalUserService.getFollowerIds(USER_ID, null, size).getIds()).isEmpty();
        }

        @Test
        void should_throw_invalid_cursor_and_not_query_when_the_cursor_is_malformed() {
            assertThatThrownBy(() -> internalUserService.getFollowerIds(USER_ID, "not-a-cursor", 20))
                    .isInstanceOf(InvalidCursorException.class);
            verifyNoInteractions(followRepository);
        }
    }

    @Nested
    class IsFollowing {

        @Test
        void should_return_true_when_the_follower_follows_the_followee() {
            UUID followeeId = UUID.randomUUID();
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, followeeId)).thenReturn(true);

            assertThat(internalUserService.isFollowing(USER_ID, followeeId)).isTrue();
        }

        @Test
        void should_return_false_when_the_follower_does_not_follow_the_followee() {
            UUID followeeId = UUID.randomUUID();
            when(followRepository.existsByFollowerIdAndFollowingId(USER_ID, followeeId)).thenReturn(false);

            assertThat(internalUserService.isFollowing(USER_ID, followeeId)).isFalse();
        }

        @Test
        void should_ask_for_the_follower_and_the_followee_in_that_order() {
            UUID followeeId = UUID.randomUUID();

            internalUserService.isFollowing(USER_ID, followeeId);

            verify(followRepository).existsByFollowerIdAndFollowingId(USER_ID, followeeId);
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class GetUsers {

        @Test
        void should_return_the_id_username_and_picture_url_of_each_confirmed_user() {
            User withPicture = confirmedUser();
            DbFile picture = TestEntities.newDbFile();
            picture.setId(UUID.randomUUID());
            withPicture.setProfilePicture(picture);
            User withoutPicture = confirmedUser();
            when(userRepository.findAllById(any())).thenReturn(List.of(withPicture, withoutPicture));

            List<InternalUserResponseDTO> response =
                    internalUserService.getUsers(List.of(withPicture.getId(), withoutPicture.getId()));

            assertThat(response)
                    .extracting(
                            InternalUserResponseDTO::getId,
                            InternalUserResponseDTO::getUsername,
                            InternalUserResponseDTO::getProfilePictureUrl)
                    .containsExactly(
                            tuple(
                                    withPicture.getId(), withPicture.getUsername(), "/api/v1/files/" + picture.getId()),
                            tuple(
                                    withoutPicture.getId(), withoutPicture.getUsername(), null));
        }

        @Test
        void should_leave_out_a_user_who_has_not_confirmed_the_account() {
            User confirmed = confirmedUser();
            User pending = confirmedUser();
            pending.setEnabled(false);
            when(userRepository.findAllById(any())).thenReturn(List.of(confirmed, pending));

            List<InternalUserResponseDTO> response =
                    internalUserService.getUsers(List.of(confirmed.getId(), pending.getId()));

            assertThat(response)
                    .extracting(InternalUserResponseDTO::getId)
                    .containsExactly(confirmed.getId());
        }

        @Test
        void should_query_each_id_once_when_ids_repeat() {
            UUID id = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            when(userRepository.findAllById(any())).thenReturn(List.of());

            internalUserService.getUsers(List.of(id, other, id));

            ArgumentCaptor<Iterable<UUID>> queried = ArgumentCaptor.captor();
            verify(userRepository).findAllById(queried.capture());
            assertThat(queried.getValue()).containsExactly(id, other);
        }

        @Test
        void should_accept_exactly_100_ids() {
            when(userRepository.findAllById(any())).thenReturn(List.of());

            assertThat(internalUserService.getUsers(randomIds(100))).isEmpty();
        }

        @Test
        void should_throw_out_of_range_and_not_query_when_no_ids_are_given() {
            assertThatThrownBy(() -> internalUserService.getUsers(List.of()))
                    .isInstanceOf(UserIdsOutOfRangeException.class)
                    .hasMessage("Provide between 1 and 100 user ids.");
            verifyNoInteractions(userRepository);
        }

        @Test
        void should_throw_out_of_range_and_not_query_when_101_ids_are_given() {
            assertThatThrownBy(() -> internalUserService.getUsers(randomIds(101)))
                    .isInstanceOf(UserIdsOutOfRangeException.class);
            verifyNoInteractions(userRepository);
        }

        private User confirmedUser() {
            User user = TestEntities.newUser();
            user.setId(UUID.randomUUID());
            user.setEnabled(true);

            return user;
        }

        private List<UUID> randomIds(int count) {
            return Stream
                    .generate(UUID::randomUUID)
                    .limit(count)
                    .toList();
        }
    }

    private FollowerEdge edge(Instant createdAt) {
        return new StubFollowerEdge(UUID.randomUUID(), UUID.randomUUID(), createdAt);
    }

    @Getter
    @AllArgsConstructor
    private static class StubFollowerEdge implements FollowerEdge {

        private final UUID followId;

        private final UUID followerId;

        private final Instant createdAt;
    }
}
