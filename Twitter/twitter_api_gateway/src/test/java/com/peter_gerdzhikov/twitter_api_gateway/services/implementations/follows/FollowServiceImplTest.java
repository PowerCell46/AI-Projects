package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.follows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.SelfFollowException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox.OutboxService;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    private static final String FOLLOWED_TOPIC = "user.followed";

    private static final String UNFOLLOWED_TOPIC = "user.unfollowed";

    private static final UUID SMALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID LARGER_ID = UUID.fromString("7fffffff-ffff-ffff-7fff-ffffffffffff");

    private static final String TARGET_USERNAME = "Target";

    private static final String TARGET_EMAIL = "target@example.com";

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00.123456789Z");

    private FollowServiceImpl followService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        followService = new FollowServiceImpl(
                clock, FOLLOWED_TOPIC, UNFOLLOWED_TOPIC, outboxService, userRepository, followRepository);
    }

    @Nested
    class FollowWithoutEvent {

        @Test
        void should_insert_the_follow_and_update_the_counts_without_queueing_an_event() {
            givenConfirmedTarget("target", LARGER_ID);
            givenInsertReturns(SMALLER_ID, LARGER_ID, 1);

            followService.followWithoutEvent(SMALLER_ID, "target");

            verify(userRepository).addToFollowingCount(SMALLER_ID, 1);
            verify(userRepository).addToFollowersCount(LARGER_ID, 1);
            verifyNoInteractions(outboxService);
        }

        @Test
        void should_not_touch_the_counters_when_the_follow_already_existed() {
            givenConfirmedTarget("target", LARGER_ID);
            givenInsertReturns(SMALLER_ID, LARGER_ID, 0);

            followService.followWithoutEvent(SMALLER_ID, "target");

            verify(userRepository, never()).addToFollowingCount(any(), anyLong());
            verifyNoInteractions(outboxService);
        }

        @Test
        void should_throw_when_the_user_follows_themselves() {
            givenConfirmedTarget("me", SMALLER_ID);

            assertThatThrownBy(() -> followService.followWithoutEvent(SMALLER_ID, "me"))
                    .isInstanceOf(SelfFollowException.class);

            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Follow {

        @Test
        void should_update_the_smaller_id_first_when_the_follower_has_the_smaller_id() {
            givenConfirmedTarget("target", LARGER_ID);
            givenFollower(SMALLER_ID, "follower");
            givenInsertReturns(SMALLER_ID, LARGER_ID, 1);

            followService.follow(SMALLER_ID, "target");

            InOrder order = inOrder(userRepository);
            order.verify(userRepository).addToFollowingCount(SMALLER_ID, 1);
            order.verify(userRepository).addToFollowersCount(LARGER_ID, 1);
        }

        @Test
        void should_update_the_smaller_id_first_when_the_target_has_the_smaller_id() {
            givenConfirmedTarget("target", SMALLER_ID);
            givenFollower(LARGER_ID, "follower");
            givenInsertReturns(LARGER_ID, SMALLER_ID, 1);

            followService.follow(LARGER_ID, "target");

            InOrder order = inOrder(userRepository);
            order.verify(userRepository).addToFollowersCount(SMALLER_ID, 1);
            order.verify(userRepository).addToFollowingCount(LARGER_ID, 1);
        }

        @Test
        void should_not_touch_the_counters_when_the_follow_already_existed() {
            givenConfirmedTarget("target", LARGER_ID);
            givenInsertReturns(SMALLER_ID, LARGER_ID, 0);

            followService.follow(SMALLER_ID, "target");

            verify(userRepository, never()).addToFollowingCount(any(), anyLong());
            verify(userRepository, never()).addToFollowersCount(any(), anyLong());
        }

        @Test
        void should_insert_with_a_created_at_truncated_to_microseconds() {
            givenConfirmedTarget("target", LARGER_ID);
            givenFollower(SMALLER_ID, "follower");
            givenInsertReturns(SMALLER_ID, LARGER_ID, 1);

            followService.follow(SMALLER_ID, "target");

            ArgumentCaptor<Instant> createdAt = ArgumentCaptor.forClass(Instant.class);
            verify(followRepository).insertIfAbsent(any(), eq(SMALLER_ID), eq(LARGER_ID), createdAt.capture());
            assertThat(createdAt.getValue()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
        }

        @Test
        void should_look_the_target_up_by_the_lowercased_username() {
            givenConfirmedTarget("peterg", LARGER_ID);
            givenInsertReturns(SMALLER_ID, LARGER_ID, 0);

            followService.follow(SMALLER_ID, "PeterG");

            verify(userRepository).findByUsernameNormalized("peterg");
        }

        @Test
        void should_throw_before_writing_when_the_user_follows_themselves() {
            givenConfirmedTarget("me", SMALLER_ID);

            assertThatThrownBy(() -> followService.follow(SMALLER_ID, "me"))
                    .isInstanceOf(SelfFollowException.class);

            verifyNoInteractions(followRepository);
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        void should_throw_when_the_target_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.follow(SMALLER_ID, "nobody"))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(followRepository);
        }

        @Test
        void should_throw_when_the_target_is_unconfirmed() {
            User pending = TestEntities.newUser();
            pending.setId(LARGER_ID);
            when(userRepository.findByUsernameNormalized("pending")).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> followService.follow(SMALLER_ID, "pending"))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(followRepository);
        }

        @Test
        void should_enqueue_one_event_keyed_by_the_followee_when_the_follow_is_new() {
            givenConfirmedTarget("target", LARGER_ID);
            givenFollower(SMALLER_ID, "follower");
            givenInsertReturns(SMALLER_ID, LARGER_ID, 1);

            followService.follow(SMALLER_ID, "target");

            UserFollowedEventDTO event = enqueuedEvents(1).getFirst();
            assertThat(event.getFollowerId()).isEqualTo(SMALLER_ID);
            assertThat(event.getFolloweeId()).isEqualTo(LARGER_ID);
            assertThat(event.getFollowerUsername()).isEqualTo("follower");
            assertThat(event.getFolloweeUsername()).isEqualTo(TARGET_USERNAME);
            assertThat(event.getFolloweeEmail()).isEqualTo(TARGET_EMAIL);
            assertThat(event.getOccurredAt()).isEqualTo(NOW);
            assertThat(event.getEventId()).isNotNull();
        }

        @Test
        void should_give_every_event_a_fresh_event_id() {
            givenConfirmedTarget("target", LARGER_ID);
            givenFollower(SMALLER_ID, "follower");
            givenInsertReturns(SMALLER_ID, LARGER_ID, 1);

            followService.follow(SMALLER_ID, "target");
            followService.follow(SMALLER_ID, "target");

            List<UserFollowedEventDTO> events = enqueuedEvents(2);
            assertThat(events.get(0).getEventId()).isNotEqualTo(events.get(1).getEventId());
        }

        @Test
        void should_not_enqueue_when_the_follow_already_existed() {
            givenConfirmedTarget("target", LARGER_ID);
            givenInsertReturns(SMALLER_ID, LARGER_ID, 0);

            followService.follow(SMALLER_ID, "target");

            verifyNoInteractions(outboxService);
        }

        @Test
        void should_not_enqueue_when_the_user_follows_themselves() {
            givenConfirmedTarget("me", SMALLER_ID);

            assertThatThrownBy(() -> followService.follow(SMALLER_ID, "me"))
                    .isInstanceOf(SelfFollowException.class);

            verifyNoInteractions(outboxService);
        }

        @Test
        void should_not_enqueue_when_the_target_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.follow(SMALLER_ID, "nobody"))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Unfollow {

        @Test
        void should_update_the_smaller_id_first_when_the_follower_has_the_smaller_id() {
            givenConfirmedTarget("target", LARGER_ID);
            when(followRepository.deleteByPair(SMALLER_ID, LARGER_ID)).thenReturn(1);

            followService.unfollow(SMALLER_ID, "target");

            InOrder order = inOrder(userRepository);
            order.verify(userRepository).addToFollowingCount(SMALLER_ID, -1);
            order.verify(userRepository).addToFollowersCount(LARGER_ID, -1);
        }

        @Test
        void should_update_the_smaller_id_first_when_the_target_has_the_smaller_id() {
            givenConfirmedTarget("target", SMALLER_ID);
            when(followRepository.deleteByPair(LARGER_ID, SMALLER_ID)).thenReturn(1);

            followService.unfollow(LARGER_ID, "target");

            InOrder order = inOrder(userRepository);
            order.verify(userRepository).addToFollowersCount(SMALLER_ID, -1);
            order.verify(userRepository).addToFollowingCount(LARGER_ID, -1);
        }

        @Test
        void should_not_touch_the_counters_when_there_was_no_follow() {
            givenConfirmedTarget("target", LARGER_ID);
            when(followRepository.deleteByPair(SMALLER_ID, LARGER_ID)).thenReturn(0);

            followService.unfollow(SMALLER_ID, "target");

            verify(userRepository, never()).addToFollowingCount(any(), anyLong());
            verify(userRepository, never()).addToFollowersCount(any(), anyLong());
        }

        @Test
        void should_enqueue_one_unfollowed_event_keyed_by_the_follower_when_a_follow_was_removed() {
            givenConfirmedTarget("target", LARGER_ID);
            when(followRepository.deleteByPair(SMALLER_ID, LARGER_ID)).thenReturn(1);

            followService.unfollow(SMALLER_ID, "target");

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).enqueue(eq(UNFOLLOWED_TOPIC), eq(SMALLER_ID.toString()), payload.capture());
            UserUnfollowedEventDTO event = (UserUnfollowedEventDTO) payload.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getFollowerId()).isEqualTo(SMALLER_ID);
            assertThat(event.getFolloweeId()).isEqualTo(LARGER_ID);
            assertThat(event.getOccurredAt()).isEqualTo(NOW);
            verifyNoMoreInteractions(outboxService);
        }

        @Test
        void should_not_enqueue_an_event_when_there_was_no_follow() {
            givenConfirmedTarget("target", LARGER_ID);
            when(followRepository.deleteByPair(SMALLER_ID, LARGER_ID)).thenReturn(0);

            followService.unfollow(SMALLER_ID, "target");

            verifyNoInteractions(outboxService);
        }

        @Test
        void should_throw_before_writing_when_the_user_unfollows_themselves() {
            givenConfirmedTarget("me", SMALLER_ID);

            assertThatThrownBy(() -> followService.unfollow(SMALLER_ID, "me"))
                    .isInstanceOf(SelfFollowException.class);

            verifyNoInteractions(followRepository);
        }

        @Test
        void should_throw_when_the_target_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> followService.unfollow(SMALLER_ID, "nobody"))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(followRepository);
        }
    }

    private void givenConfirmedTarget(String normalizedUsername, UUID id) {
        User target = TestEntities.newUser();
        target.setId(id);
        target.setEnabled(true);
        target.setUsername(TARGET_USERNAME);
        target.setEmail(TARGET_EMAIL);
        when(userRepository.findByUsernameNormalized(normalizedUsername)).thenReturn(Optional.of(target));
    }

    private void givenFollower(UUID id, String username) {
        User follower = TestEntities.newUser();
        follower.setId(id);
        follower.setUsername(username);
        when(userRepository.findById(id)).thenReturn(Optional.of(follower));
    }

    private List<UserFollowedEventDTO> enqueuedEvents(int expectedCalls) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService, times(expectedCalls)).enqueue(eq(FOLLOWED_TOPIC), eq(LARGER_ID.toString()), captor.capture());

        return captor.getAllValues().stream().map(UserFollowedEventDTO.class::cast).toList();
    }

    private void givenInsertReturns(UUID followerId, UUID targetId, int inserted) {
        when(followRepository.insertIfAbsent(any(), eq(followerId), eq(targetId), any())).thenReturn(inserted);
    }
}
