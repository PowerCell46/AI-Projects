package com.peter_gerdzhikov.twitter_timeline_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_timeline_service.entities.SavedTweet;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SavedTweetRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant SAVED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SavedTweetRepository savedTweetRepository;

    @Nested
    class InsertIfAbsent {

        @Test
        void should_add_one_row_and_return_one_when_the_tweet_is_new_to_the_user() {
            UUID userId = TestIds.userId();

            int added = savedTweetRepository.insertIfAbsent(userId, TestIds.tweetId(), TestIds.userId(), SAVED_AT);

            assertThat(added).isEqualTo(1);
            assertThat(savedBy(userId)).hasSize(1);
        }

        @Test
        void should_store_the_tweet_the_author_and_the_exact_microsecond_time_when_a_row_is_added() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();

            savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, SAVED_AT);
            entityManager.clear();

            SavedTweet stored = savedBy(userId).getFirst();
            assertThat(stored.getTweetId()).isEqualTo(tweetId);
            assertThat(stored.getAuthorId()).isEqualTo(authorId);
            assertThat(stored.getSavedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        void should_add_nothing_and_return_zero_when_the_same_pair_is_inserted_again() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, SAVED_AT);

            int added = savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, SAVED_AT.plusSeconds(60));

            assertThat(added).isZero();
            assertThat(savedBy(userId)).hasSize(1);
        }

        @Test
        void should_keep_the_original_saved_at_when_the_same_pair_is_inserted_later() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, SAVED_AT);

            savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, SAVED_AT.plusSeconds(60));
            entityManager.clear();

            assertThat(savedBy(userId).getFirst().getSavedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        void should_add_the_tweet_for_each_user_when_different_users_save_it() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();

            savedTweetRepository.insertIfAbsent(first, tweetId, authorId, SAVED_AT);
            savedTweetRepository.insertIfAbsent(second, tweetId, authorId, SAVED_AT);

            assertThat(savedBy(first)).hasSize(1);
            assertThat(savedBy(second)).hasSize(1);
        }
    }

    @Nested
    class KeysetPages {

        @Test
        void should_order_rows_most_recently_saved_first_and_break_ties_by_tweet_id_descending() {
            UUID userId = TestIds.userId();
            UUID oldest = save(userId, TestIds.tweetId(), SAVED_AT);
            UUID tiedLowId = save(userId, LOW_ID, SAVED_AT.plusSeconds(1));
            UUID tiedHighId = save(userId, HIGH_ID, SAVED_AT.plusSeconds(1));
            UUID newest = save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(2));

            List<SavedTweet> page = savedTweetRepository.findFirstPage(userId, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(newest, tiedHighId, tiedLowId, oldest);
        }

        @Test
        void should_return_only_the_rows_of_the_given_user() {
            UUID userId = TestIds.userId();
            UUID mine = save(userId, TestIds.tweetId(), SAVED_AT);
            save(TestIds.userId(), TestIds.tweetId(), SAVED_AT);

            assertThat(tweetIdsOf(savedTweetRepository.findFirstPage(userId, PageRequest.of(0, 10))))
                    .containsExactly(mine);
        }

        @Test
        void should_return_the_limit_when_there_are_more_rows() {
            UUID userId = TestIds.userId();
            save(userId, TestIds.tweetId(), SAVED_AT);
            save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(1));

            assertThat(savedTweetRepository.findFirstPage(userId, PageRequest.of(0, 1))).hasSize(1);
        }

        @Test
        void should_return_nothing_when_the_user_saved_nothing() {
            assertThat(savedTweetRepository.findFirstPage(TestIds.userId(), PageRequest.of(0, 10))).isEmpty();
        }

        @Test
        void should_return_only_older_rows_when_the_page_starts_after_a_position() {
            UUID userId = TestIds.userId();
            UUID older = save(userId, TestIds.tweetId(), SAVED_AT);
            UUID boundary = save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(1));
            save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(2));

            List<SavedTweet> page = savedTweetRepository.findPageAfter(
                    userId, SAVED_AT.plusSeconds(1), boundary, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(older);
        }

        @Test
        void should_keep_the_lower_tweet_ids_at_the_boundary_time_when_the_page_starts_after_a_tied_row() {
            UUID userId = TestIds.userId();
            save(userId, HIGH_ID, SAVED_AT);
            UUID tiedLowId = save(userId, LOW_ID, SAVED_AT);

            List<SavedTweet> page = savedTweetRepository.findPageAfter(
                    userId, SAVED_AT, HIGH_ID, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(tiedLowId);
        }

        @Test
        void should_cover_every_row_exactly_once_when_every_row_shares_one_timestamp() {
            UUID userId = TestIds.userId();
            List<UUID> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(save(userId, TestIds.tweetId(), SAVED_AT));
            }

            List<UUID> seen = new ArrayList<>();
            List<SavedTweet> page = savedTweetRepository.findFirstPage(userId, PageRequest.of(0, 3));
            while (!page.isEmpty()) {
                seen.addAll(tweetIdsOf(page));
                SavedTweet last = page.getLast();
                page = savedTweetRepository.findPageAfter(
                        userId, last.getSavedAt(), last.getTweetId(), PageRequest.of(0, 3));
            }

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_index_the_list_by_user_then_saved_time_then_tweet_so_it_serves_the_page_query() {
            List<String> indexColumns = jdbcTemplate.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_class c ON c.oid = i.indexrelid
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = 'saved_tweets'::regclass AND c.relname = 'ix_saved_tweets_user_saved'
                    ORDER BY array_position(i.indkey::int2[], a.attnum)
                    """, String.class);

            assertThat(indexColumns).containsExactly("user_id", "saved_at", "tweet_id");
        }

        @Test
        void should_index_the_tweet_so_the_delete_by_tweet_does_not_scan_the_table() {
            Integer indexes = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_indexes
                    WHERE tablename = 'saved_tweets' AND indexname = 'ix_saved_tweets_tweet'
                    """, Integer.class);

            assertThat(indexes).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByUserAndTweet {

        @Test
        void should_remove_the_row_and_return_one_when_the_tweet_is_saved() {
            UUID userId = TestIds.userId();
            UUID tweetId = save(userId, TestIds.tweetId(), SAVED_AT);

            int removed = savedTweetRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(removed).isEqualTo(1);
            assertThat(savedBy(userId)).isEmpty();
        }

        @Test
        void should_return_zero_when_the_tweet_is_not_saved() {
            assertThat(savedTweetRepository.deleteByUserAndTweet(TestIds.userId(), TestIds.tweetId())).isZero();
        }

        @Test
        void should_leave_the_same_tweet_saved_by_other_users() {
            UUID tweetId = TestIds.tweetId();
            UUID userId = TestIds.userId();
            UUID otherUserId = TestIds.userId();
            save(userId, tweetId, SAVED_AT);
            save(otherUserId, tweetId, SAVED_AT);

            savedTweetRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(tweetIdsOf(savedBy(otherUserId))).containsExactly(tweetId);
        }

        @Test
        void should_leave_the_users_other_tweets() {
            UUID userId = TestIds.userId();
            UUID kept = save(userId, TestIds.tweetId(), SAVED_AT);
            UUID removed = save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(1));

            savedTweetRepository.deleteByUserAndTweet(userId, removed);

            assertThat(tweetIdsOf(savedBy(userId))).containsExactly(kept);
        }

        @Test
        void should_return_zero_the_second_time_when_the_same_tweet_is_unsaved_twice() {
            UUID userId = TestIds.userId();
            UUID tweetId = save(userId, TestIds.tweetId(), SAVED_AT);
            savedTweetRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(savedTweetRepository.deleteByUserAndTweet(userId, tweetId)).isZero();
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_the_tweet_from_every_saved_list_and_return_the_count() {
            UUID tweetId = TestIds.tweetId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();
            save(first, tweetId, SAVED_AT);
            save(second, tweetId, SAVED_AT);

            int removed = savedTweetRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(2);
            assertThat(savedBy(first)).isEmpty();
            assertThat(savedBy(second)).isEmpty();
        }

        @Test
        void should_leave_other_tweets_untouched() {
            UUID userId = TestIds.userId();
            UUID deleted = save(userId, TestIds.tweetId(), SAVED_AT.plusSeconds(1));
            UUID kept = save(userId, TestIds.tweetId(), SAVED_AT);

            savedTweetRepository.deleteByTweetId(deleted);

            assertThat(tweetIdsOf(savedBy(userId))).containsExactly(kept);
        }

        @Test
        void should_return_zero_when_the_tweet_is_in_no_saved_list() {
            assertThat(savedTweetRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_return_zero_the_second_time_when_the_same_tweet_is_deleted_twice() {
            UUID tweetId = save(TestIds.userId(), TestIds.tweetId(), SAVED_AT);
            savedTweetRepository.deleteByTweetId(tweetId);

            assertThat(savedTweetRepository.deleteByTweetId(tweetId)).isZero();
        }
    }

    private UUID save(UUID userId, UUID tweetId, Instant savedAt) {
        savedTweetRepository.insertIfAbsent(userId, tweetId, TestIds.userId(), savedAt);

        return tweetId;
    }

    private List<SavedTweet> savedBy(UUID userId) {
        return savedTweetRepository.findFirstPage(userId, PageRequest.of(0, 100));
    }

    private List<UUID> tweetIdsOf(List<SavedTweet> savedTweets) {
        return savedTweets
                .stream()
                .map(SavedTweet::getTweetId)
                .toList();
    }
}
