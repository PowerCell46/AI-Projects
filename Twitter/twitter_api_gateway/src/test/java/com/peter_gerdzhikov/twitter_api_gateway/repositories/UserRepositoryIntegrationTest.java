package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant CUTOFF = Instant.parse("2026-01-08T00:00:00Z");

    private static final Instant NEWEST_BASE = Instant.parse("2999-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    class Normalization {

        @Test
        void should_lowercase_the_email_before_persisting() {
            User user = TestEntities.newUser();
            user.setEmail("Mixed.Case@Example.TEST");

            User saved = userRepository.saveAndFlush(user);

            assertThat(saved.getEmail()).isEqualTo("mixed.case@example.test");
        }

        @Test
        void should_keep_the_username_as_typed_and_store_a_lowercase_copy() {
            User user = TestEntities.newUser();
            user.setUsername("PeterG_1");

            User saved = userRepository.saveAndFlush(user);

            assertThat(saved.getUsername()).isEqualTo("PeterG_1");
            assertThat(saved.getUsernameNormalized()).isEqualTo("peterg_1");
        }

        @Test
        void should_refresh_the_lowercased_email_on_update() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());

            saved.setEmail("Changed@Example.TEST");
            userRepository.saveAndFlush(saved);

            assertThat(saved.getEmail()).isEqualTo("changed@example.test");
        }

        @Test
        void should_default_to_disabled() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());

            assertThat(saved.isEnabled()).isFalse();
        }
    }

    @Nested
    class UniqueConstraints {

        @Test
        void should_reject_a_duplicate_email_differing_only_in_case() {
            User first = userRepository.saveAndFlush(TestEntities.newUser());
            User second = TestEntities.newUser();
            second.setEmail(first.getEmail().toUpperCase());

            assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_users_email");
        }

        @Test
        void should_reject_a_duplicate_username_differing_only_in_case() {
            User first = userRepository.saveAndFlush(TestEntities.newUser());
            User second = TestEntities.newUser();
            second.setUsername(first.getUsername().toUpperCase());

            assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_users_username_normalized");
        }
    }

    @Nested
    class ProfileFields {

        @Test
        void should_default_every_profile_field_to_null() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());
            entityManager.clear();

            User found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getBio()).isNull();
            assertThat(found.getLocation()).isNull();
            assertThat(found.getBirthdate()).isNull();
            assertThat(found.getProfilePicture()).isNull();
            assertThat(found.getProfileCoverPicture()).isNull();
        }

        @Test
        void should_persist_the_text_fields_and_birthdate() {
            User user = TestEntities.newUser();
            user.setBio("b".repeat(160));
            user.setLocation("l".repeat(30));
            user.setBirthdate(LocalDate.of(1990, 5, 17));
            User saved = userRepository.saveAndFlush(user);
            entityManager.clear();

            User found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getBio()).isEqualTo("b".repeat(160));
            assertThat(found.getLocation()).isEqualTo("l".repeat(30));
            assertThat(found.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 17));
        }

        @Test
        void should_reject_a_birthdate_that_is_not_in_the_past() {
            User user = TestEntities.newUser();
            user.setBirthdate(LocalDate.now().plusDays(1));

            assertThatThrownBy(() -> userRepository.saveAndFlush(user))
                    .isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        void should_point_both_picture_slots_at_their_own_files() {
            DbFile profilePicture = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
            DbFile coverPicture = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
            User user = TestEntities.newUser();
            user.setProfilePicture(profilePicture);
            user.setProfileCoverPicture(coverPicture);
            User saved = userRepository.saveAndFlush(user);
            entityManager.clear();

            User found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getProfilePicture().getId()).isEqualTo(profilePicture.getId());
            assertThat(found.getProfileCoverPicture().getId()).isEqualTo(coverPicture.getId());
        }

        @Test
        void should_reject_two_users_pointing_at_the_same_file() {
            DbFile shared = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
            User first = TestEntities.newUser();
            first.setProfilePicture(shared);
            userRepository.saveAndFlush(first);
            User second = TestEntities.newUser();
            second.setProfilePicture(shared);

            assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void should_reject_deleting_a_file_a_user_still_points_at() {
            DbFile file = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
            User user = TestEntities.newUser();
            user.setProfilePicture(file);
            userRepository.saveAndFlush(user);

            assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM db_files WHERE id = ?", file.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class DynamicUpdate {

        @Test
        void should_write_only_the_changed_column_when_another_column_was_changed_concurrently() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());
            DbFile file = dbFileRepository.saveAndFlush(TestEntities.newDbFile());
            jdbcTemplate.update("UPDATE users SET bio = ? WHERE id = ?", "written by a racing edit", user.getId());

            user.setProfilePicture(file);
            userRepository.flush();
            entityManager.clear();

            User found = userRepository.findById(user.getId()).orElseThrow();
            assertThat(found.getBio()).isEqualTo("written by a racing edit");
            assertThat(found.getProfilePicture().getId()).isEqualTo(file.getId());
        }
    }

    @Nested
    class Lookups {

        @Test
        void should_find_a_user_by_email() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());

            assertThat(userRepository.findByEmail(saved.getEmail())).isPresent();
            assertThat(userRepository.existsByEmail(saved.getEmail())).isTrue();
        }

        @Test
        void should_find_a_user_by_normalized_username() {
            User user = TestEntities.newUser();
            user.setUsername("PeterG_2");
            userRepository.saveAndFlush(user);

            assertThat(userRepository.findByUsernameNormalized("peterg_2")).isPresent();
            assertThat(userRepository.existsByUsernameNormalized("peterg_2")).isTrue();
        }

        @Test
        void should_not_find_a_user_by_the_username_as_typed_when_it_has_uppercase() {
            User user = TestEntities.newUser();
            user.setUsername("PeterG_3");
            userRepository.saveAndFlush(user);

            assertThat(userRepository.findByUsernameNormalized("PeterG_3")).isEmpty();
        }

        @Test
        void should_report_absence_for_an_unknown_email_and_username() {
            User unknown = TestEntities.newUser();

            assertThat(userRepository.existsByEmail(unknown.getEmail())).isFalse();
            assertThat(userRepository.existsByUsernameNormalized(unknown.getUsername())).isFalse();
        }
    }

    @Nested
    class LockById {

        @Test
        void should_return_the_id_of_an_existing_user() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());

            assertThat(userRepository.lockById(saved.getId())).contains(saved.getId());
        }

        @Test
        void should_return_empty_for_an_unknown_id() {
            assertThat(userRepository.lockById(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    class DeleteUnconfirmedCreatedBefore {

        @Test
        void should_delete_unconfirmed_users_created_before_the_cutoff() {
            User old = savedUserCreatedAt(false, CUTOFF.minusSeconds(60));

            int deleted = userRepository.deleteUnconfirmedCreatedBefore(CUTOFF);

            assertThat(deleted).isEqualTo(1);
            assertThat(userRepository.findById(old.getId())).isEmpty();
        }

        @Test
        void should_keep_unconfirmed_users_created_after_the_cutoff() {
            User recent = savedUserCreatedAt(false, CUTOFF.plusSeconds(60));

            int deleted = userRepository.deleteUnconfirmedCreatedBefore(CUTOFF);

            assertThat(deleted).isZero();
            assertThat(userRepository.findById(recent.getId())).isPresent();
        }

        @Test
        void should_keep_confirmed_users_created_before_the_cutoff() {
            User confirmed = savedUserCreatedAt(true, CUTOFF.minusSeconds(60));

            int deleted = userRepository.deleteUnconfirmedCreatedBefore(CUTOFF);

            assertThat(deleted).isZero();
            assertThat(userRepository.findById(confirmed.getId())).isPresent();
        }
    }

    @Nested
    class FollowCounts {

        @Test
        void should_default_both_counts_to_zero() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());
            entityManager.clear();

            User found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getFollowersCount()).isZero();
            assertThat(found.getFollowingCount()).isZero();
        }

        @Test
        void should_not_write_a_count_set_on_the_entity_when_the_user_is_flushed() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            user.setFollowersCount(99);
            user.setFollowingCount(99);
            user.setBio("forces an update");
            userRepository.flush();
            entityManager.clear();

            User found = userRepository.findById(user.getId()).orElseThrow();
            assertThat(found.getFollowersCount()).isZero();
            assertThat(found.getFollowingCount()).isZero();
        }

        @Test
        void should_reject_a_negative_followers_count() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE users SET followers_count = -1 WHERE id = ?",
                    user.getId()
            ))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(User.FOLLOW_COUNTS_CONSTRAINT);
        }

        @Test
        void should_reject_a_negative_following_count() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE users SET following_count = -1 WHERE id = ?",
                    user.getId()
            ))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(User.FOLLOW_COUNTS_CONSTRAINT);
        }
    }

    @Nested
    class AddToFollowersCount {

        @Test
        void should_increment_the_followers_count_and_return_one_row() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            int changed = userRepository.addToFollowersCount(user.getId(), 1);

            assertThat(changed).isEqualTo(1);
            assertThat(reload(user).getFollowersCount()).isEqualTo(1);
        }

        @Test
        void should_decrement_the_followers_count() {
            User user = userWithCounts(3, 0);

            userRepository.addToFollowersCount(user.getId(), -1);

            assertThat(reload(user).getFollowersCount()).isEqualTo(2);
        }

        @Test
        void should_leave_the_following_count_and_other_users_untouched() {
            User user = userWithCounts(0, 5);
            User other = userWithCounts(7, 7);

            userRepository.addToFollowersCount(user.getId(), 1);

            assertThat(reload(user).getFollowingCount()).isEqualTo(5);
            assertThat(reload(other).getFollowersCount()).isEqualTo(7);
            assertThat(reload(other).getFollowingCount()).isEqualTo(7);
        }

        @Test
        void should_reject_a_decrement_below_zero() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            assertThatThrownBy(() -> userRepository.addToFollowersCount(user.getId(), -1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(User.FOLLOW_COUNTS_CONSTRAINT);
        }

        @Test
        void should_return_zero_rows_when_the_user_is_unknown() {
            assertThat(userRepository.addToFollowersCount(UUID.randomUUID(), 1)).isZero();
        }
    }

    @Nested
    class AddToFollowingCount {

        @Test
        void should_increment_the_following_count_and_return_one_row() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            int changed = userRepository.addToFollowingCount(user.getId(), 1);

            assertThat(changed).isEqualTo(1);
            assertThat(reload(user).getFollowingCount()).isEqualTo(1);
        }

        @Test
        void should_decrement_the_following_count() {
            User user = userWithCounts(0, 3);

            userRepository.addToFollowingCount(user.getId(), -1);

            assertThat(reload(user).getFollowingCount()).isEqualTo(2);
        }

        @Test
        void should_leave_the_followers_count_and_other_users_untouched() {
            User user = userWithCounts(5, 0);
            User other = userWithCounts(7, 7);

            userRepository.addToFollowingCount(user.getId(), 1);

            assertThat(reload(user).getFollowersCount()).isEqualTo(5);
            assertThat(reload(other).getFollowersCount()).isEqualTo(7);
            assertThat(reload(other).getFollowingCount()).isEqualTo(7);
        }

        @Test
        void should_reject_a_decrement_below_zero() {
            User user = userRepository.saveAndFlush(TestEntities.newUser());

            assertThatThrownBy(() -> userRepository.addToFollowingCount(user.getId(), -1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(User.FOLLOW_COUNTS_CONSTRAINT);
        }

        @Test
        void should_return_zero_rows_when_the_user_is_unknown() {
            assertThat(userRepository.addToFollowingCount(UUID.randomUUID(), 1)).isZero();
        }
    }

    @Nested
    class UserListFirstPage {

        @Test
        void should_return_the_users_newest_first() {
            User oldest = savedUserCreatedAt(true, NEWEST_BASE);
            User newest = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            User middle = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));

            List<User> page = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 3));

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        void should_leave_out_the_caller() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(30));
            User newer = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            User older = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));

            List<User> page = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 2));

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        void should_leave_out_unconfirmed_users() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            savedUserCreatedAt(false, NEWEST_BASE.plusSeconds(30));
            User newer = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            User older = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));

            List<User> page = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 2));

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        void should_return_at_most_the_requested_number_of_rows() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            User newest = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(30));
            User second = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));

            List<User> page = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 2));

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newest.getId(), second.getId());
        }

        @Test
        void should_return_the_users_with_equal_created_at_ordered_by_id_descending() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            List<UUID> sameInstantIds = savedUsersCreatedAt(3, NEWEST_BASE);

            List<User> page = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 3));

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactlyElementsOf(descendingByPostgresOrder(sameInstantIds));
        }
    }

    @Nested
    class UserListPageAfterCursor {

        @Test
        void should_return_the_users_strictly_older_than_the_cursor_newest_first() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            User cursorUser = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(30));
            User newer = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));
            User older = savedUserCreatedAt(true, NEWEST_BASE);

            List<User> page = pageAfter(caller, cursorUser, NEWEST_BASE.plusSeconds(20), 2);

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        void should_leave_out_the_caller() {
            User cursorUser = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(40));
            User caller = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(30));
            User newer = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            User older = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));

            List<User> page = pageAfter(caller, cursorUser, NEWEST_BASE.plusSeconds(40), 2);

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        void should_leave_out_unconfirmed_users() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            User cursorUser = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(40));
            savedUserCreatedAt(false, NEWEST_BASE.plusSeconds(30));
            User newer = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(20));
            User older = savedUserCreatedAt(true, NEWEST_BASE.plusSeconds(10));

            List<User> page = pageAfter(caller, cursorUser, NEWEST_BASE.plusSeconds(40), 2);

            assertThat(page)
                    .extracting(User::getId)
                    .containsExactly(newer.getId(), older.getId());
        }

        @Test
        void should_break_equal_created_at_by_id_with_no_gap_or_repeat_across_pages() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE.minusSeconds(10));
            List<UUID> sameInstantIds = savedUsersCreatedAt(5, NEWEST_BASE);

            List<User> firstPage = userRepository.findUserListFirstPage(caller.getId(), PageRequest.of(0, 2));
            List<User> secondPage = pageAfter(caller, lastOf(firstPage), NEWEST_BASE, 2);
            List<User> thirdPage = pageAfter(caller, lastOf(secondPage), NEWEST_BASE, 1);

            List<UUID> walked = new ArrayList<>();
            Stream
                    .of(firstPage, secondPage, thirdPage)
                    .flatMap(List::stream)
                    .forEach(user -> walked.add(user.getId()));
            assertThat(walked).containsExactlyElementsOf(descendingByPostgresOrder(sameInstantIds));
        }

        @Test
        void should_return_nothing_when_the_cursor_is_the_oldest_row() {
            User caller = savedUserCreatedAt(true, NEWEST_BASE);
            User oldest = savedUserCreatedAt(true, Instant.EPOCH);

            List<User> page = pageAfter(caller, oldest, Instant.EPOCH, 10);

            assertThat(page).isEmpty();
        }
    }

    @Nested
    class UserListIndex {

        @Test
        void should_create_the_enabled_created_at_id_index_on_the_users_table() {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE tablename = 'users' AND indexname = ?",
                    String.class,
                    "ix_users_enabled_created"
            );

            assertThat(definitions)
                    .singleElement()
                    .asString()
                    .contains("(enabled, created_at, id)");
        }
    }

    private List<User> pageAfter(User caller, User cursorUser, Instant cursorCreatedAt, int size) {
        return userRepository.findUserListAfter(
                caller.getId(),
                cursorCreatedAt,
                cursorUser.getId(),
                PageRequest.of(0, size)
        );
    }

    private User lastOf(List<User> page) {
        return page.getLast();
    }

    private List<UUID> savedUsersCreatedAt(int count, Instant createdAt) {
        return IntStream
                .range(0, count)
                .mapToObj(index -> savedUserCreatedAt(true, createdAt).getId())
                .toList();
    }

    private List<UUID> descendingByPostgresOrder(List<UUID> ids) {
        return ids
                .stream()
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();
    }

    private User savedUserCreatedAt(boolean enabled, Instant createdAt) {
        User user = TestEntities.newUser();
        user.setEnabled(enabled);
        User saved = userRepository.saveAndFlush(user);
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                saved.getId()
        );
        entityManager.clear();

        return saved;
    }

    private User userWithCounts(long followersCount, long followingCount) {
        User user = userRepository.saveAndFlush(TestEntities.newUser());
        jdbcTemplate.update(
                "UPDATE users SET followers_count = ?, following_count = ? WHERE id = ?",
                followersCount,
                followingCount,
                user.getId()
        );
        entityManager.clear();

        return user;
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }
}
