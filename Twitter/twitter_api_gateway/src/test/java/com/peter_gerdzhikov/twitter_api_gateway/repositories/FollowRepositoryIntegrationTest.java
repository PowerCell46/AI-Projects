package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import com.peter_gerdzhikov.twitter_api_gateway.entities.Follow;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FollowRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Nested
    class InsertIfAbsent {

        @Test
        void should_return_one_and_store_the_row_when_the_pair_is_new() {
            User follower = savedUser();
            User target = savedUser();
            UUID id = UUID.randomUUID();

            int inserted = followRepository.insertIfAbsent(id, follower.getId(), target.getId(), CREATED_AT);

            Follow stored = followRepository.findById(id).orElseThrow();
            assertThat(inserted).isEqualTo(1);
            assertThat(stored.getFollower().getId()).isEqualTo(follower.getId());
            assertThat(stored.getFollowing().getId()).isEqualTo(target.getId());
        }

        @Test
        void should_round_trip_created_at_exactly_when_it_has_microsecond_precision() {
            UUID id = UUID.randomUUID();

            followRepository.insertIfAbsent(id, savedUser().getId(), savedUser().getId(), CREATED_AT);
            entityManager.clear();

            assertThat(followRepository.findById(id).orElseThrow().getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        void should_return_zero_and_keep_the_first_row_when_the_pair_already_exists() {
            User follower = savedUser();
            User target = savedUser();
            UUID firstId = UUID.randomUUID();
            followRepository.insertIfAbsent(firstId, follower.getId(), target.getId(), CREATED_AT);

            int inserted = followRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    follower.getId(),
                    target.getId(),
                    CREATED_AT.plusSeconds(60)
            );

            assertThat(inserted).isZero();
            assertThat(followRepository.count()).isEqualTo(1);
            assertThat(followRepository.findById(firstId).orElseThrow().getCreatedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        void should_store_both_rows_when_two_users_follow_each_other() {
            User first = savedUser();
            User second = savedUser();

            int forward = followRepository.insertIfAbsent(UUID.randomUUID(), first.getId(), second.getId(), CREATED_AT);
            int back = followRepository.insertIfAbsent(UUID.randomUUID(), second.getId(), first.getId(), CREATED_AT);

            assertThat(forward).isEqualTo(1);
            assertThat(back).isEqualTo(1);
        }

        @Test
        void should_reject_a_self_follow() {
            User user = savedUser();

            assertThatThrownBy(() -> followRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    user.getId(),
                    user.getId(),
                    CREATED_AT
            ))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(Follow.NO_SELF_FOLLOW_CONSTRAINT);
        }

        @Test
        void should_reject_a_follow_of_an_unknown_user() {
            User follower = savedUser();

            assertThatThrownBy(() -> followRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    follower.getId(),
                    UUID.randomUUID(),
                    CREATED_AT
            ))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_follows_following");
        }
    }

    @Nested
    class DeleteByPair {

        @Test
        void should_return_one_and_remove_the_row_when_the_follow_exists() {
            User follower = savedUser();
            User target = savedUser();
            UUID id = UUID.randomUUID();
            followRepository.insertIfAbsent(id, follower.getId(), target.getId(), CREATED_AT);

            int deleted = followRepository.deleteByPair(follower.getId(), target.getId());

            assertThat(deleted).isEqualTo(1);
            assertThat(followRepository.findById(id)).isEmpty();
        }

        @Test
        void should_return_zero_when_the_delete_is_repeated() {
            User follower = savedUser();
            User target = savedUser();
            followRepository.insertIfAbsent(UUID.randomUUID(), follower.getId(), target.getId(), CREATED_AT);
            followRepository.deleteByPair(follower.getId(), target.getId());

            assertThat(followRepository.deleteByPair(follower.getId(), target.getId())).isZero();
        }

        @Test
        void should_return_zero_when_the_pair_never_existed() {
            assertThat(followRepository.deleteByPair(savedUser().getId(), savedUser().getId())).isZero();
        }

        @Test
        void should_keep_the_reverse_follow_and_other_follows() {
            User first = savedUser();
            User second = savedUser();
            User third = savedUser();
            UUID reverseId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            followRepository.insertIfAbsent(UUID.randomUUID(), first.getId(), second.getId(), CREATED_AT);
            followRepository.insertIfAbsent(reverseId, second.getId(), first.getId(), CREATED_AT);
            followRepository.insertIfAbsent(otherId, first.getId(), third.getId(), CREATED_AT);

            followRepository.deleteByPair(first.getId(), second.getId());

            assertThat(followRepository.findById(reverseId)).isPresent();
            assertThat(followRepository.findById(otherId)).isPresent();
        }
    }

    @Nested
    class ExistsByFollowerIdAndFollowingId {

        @Test
        void should_return_true_when_the_follow_exists() {
            User follower = savedUser();
            User target = savedUser();
            followRepository.insertIfAbsent(UUID.randomUUID(), follower.getId(), target.getId(), CREATED_AT);

            assertThat(followRepository.existsByFollowerIdAndFollowingId(follower.getId(), target.getId())).isTrue();
        }

        @Test
        void should_return_false_when_only_the_reverse_follow_exists() {
            User follower = savedUser();
            User target = savedUser();
            followRepository.insertIfAbsent(UUID.randomUUID(), target.getId(), follower.getId(), CREATED_AT);

            assertThat(followRepository.existsByFollowerIdAndFollowingId(follower.getId(), target.getId())).isFalse();
        }

        @Test
        void should_return_false_when_there_is_no_follow() {
            assertThat(followRepository.existsByFollowerIdAndFollowingId(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        }
    }

    @Nested
    class FindFollowedIds {

        @Test
        void should_return_only_the_candidates_the_follower_follows() {
            User follower = savedUser();
            User followed = savedUser();
            User notFollowed = savedUser();
            User followsTheFollower = savedUser();
            follow(follower, followed, CREATED_AT);
            follow(followsTheFollower, follower, CREATED_AT);

            List<UUID> found = followRepository.findFollowedIds(
                    follower.getId(),
                    List.of(followed.getId(), notFollowed.getId(), followsTheFollower.getId())
            );

            assertThat(found).containsExactly(followed.getId());
        }

        @Test
        void should_ignore_follows_that_are_not_among_the_candidates() {
            User follower = savedUser();
            User followed = savedUser();
            follow(follower, followed, CREATED_AT);

            assertThat(followRepository.findFollowedIds(follower.getId(), List.of(savedUser().getId()))).isEmpty();
        }
    }

    @Nested
    class KeysetPages {

        @Test
        void should_order_followers_newest_first_and_break_ties_by_id_descending() {
            User target = savedUser();
            Follow oldest = follow(savedUser(), target, CREATED_AT);
            Follow tiedLowId = follow(LOW_ID, savedUser(), target, CREATED_AT.plusSeconds(1));
            Follow tiedHighId = follow(HIGH_ID, savedUser(), target, CREATED_AT.plusSeconds(1));
            Follow newest = follow(savedUser(), target, CREATED_AT.plusSeconds(2));

            List<Follow> page = followRepository.findFollowersFirstPage(target.getId(), PageRequest.of(0, 10));

            assertThat(idsOf(page)).containsExactly(newest.getId(), tiedHighId.getId(), tiedLowId.getId(), oldest.getId());
        }

        @Test
        void should_cover_every_follower_exactly_once_when_every_follow_shares_one_timestamp() {
            User target = savedUser();
            List<UUID> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(follow(savedUser(), target, CREATED_AT).getId());
            }

            List<UUID> seen = new ArrayList<>();
            List<Follow> page = followRepository.findFollowersFirstPage(target.getId(), PageRequest.of(0, 3));
            while (!page.isEmpty()) {
                seen.addAll(idsOf(page));
                Follow last = page.getLast();
                page = followRepository.findFollowersAfter(target.getId(), last.getCreatedAt(), last.getId(), PageRequest.of(0, 3));
            }

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_cover_every_followed_user_exactly_once_when_every_follow_shares_one_timestamp() {
            User source = savedUser();
            List<UUID> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(follow(source, savedUser(), CREATED_AT).getId());
            }

            List<UUID> seen = new ArrayList<>();
            List<Follow> page = followRepository.findFollowingFirstPage(source.getId(), PageRequest.of(0, 3));
            while (!page.isEmpty()) {
                seen.addAll(idsOf(page));
                Follow last = page.getLast();
                page = followRepository.findFollowingAfter(source.getId(), last.getCreatedAt(), last.getId(), PageRequest.of(0, 3));
            }

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_return_only_follows_of_the_given_user() {
            User target = savedUser();
            User other = savedUser();
            Follow mine = follow(savedUser(), target, CREATED_AT);
            follow(savedUser(), other, CREATED_AT);
            follow(target, savedUser(), CREATED_AT);

            assertThat(idsOf(followRepository.findFollowersFirstPage(target.getId(), PageRequest.of(0, 10))))
                    .containsExactly(mine.getId());
        }

        @Test
        void should_return_the_same_row_set_at_the_limit_boundary_when_the_limit_is_one() {
            User target = savedUser();
            follow(savedUser(), target, CREATED_AT);
            follow(savedUser(), target, CREATED_AT.plusSeconds(1));

            assertThat(followRepository.findFollowersFirstPage(target.getId(), PageRequest.of(0, 1))).hasSize(1);
        }

        private List<UUID> idsOf(List<Follow> follows) {
            return follows
                    .stream()
                    .map(Follow::getId)
                    .toList();
        }
    }

    @Nested
    class CascadeOnUserDelete {

        @Test
        void should_delete_the_follow_when_the_follower_is_deleted() {
            User follower = savedUser();
            UUID id = UUID.randomUUID();
            followRepository.insertIfAbsent(id, follower.getId(), savedUser().getId(), CREATED_AT);

            deleteUser(follower);

            assertThat(followRepository.findById(id)).isEmpty();
        }

        @Test
        void should_delete_the_follow_when_the_followed_user_is_deleted() {
            User target = savedUser();
            UUID id = UUID.randomUUID();
            followRepository.insertIfAbsent(id, savedUser().getId(), target.getId(), CREATED_AT);

            deleteUser(target);

            assertThat(followRepository.findById(id)).isEmpty();
        }

        private void deleteUser(User user) {
            userRepository.deleteById(user.getId());
            userRepository.flush();
            entityManager.clear();
        }
    }

    private Follow follow(User follower, User target, Instant createdAt) {
        return follow(UUID.randomUUID(), follower, target, createdAt);
    }

    private Follow follow(UUID id, User follower, User target, Instant createdAt) {
        followRepository.insertIfAbsent(id, follower.getId(), target.getId(), createdAt);

        return followRepository.findById(id).orElseThrow();
    }

    private User savedUser() {
        return userRepository.saveAndFlush(TestEntities.newUser());
    }
}
