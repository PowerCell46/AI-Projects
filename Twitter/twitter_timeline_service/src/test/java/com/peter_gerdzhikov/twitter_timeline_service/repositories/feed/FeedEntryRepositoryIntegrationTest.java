package com.peter_gerdzhikov.twitter_timeline_service.repositories.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_timeline_service.entities.feed.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.entities.feed.FeedEntryId;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FeedEntryRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant TWEET_CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FeedEntryRepository feedEntryRepository;

    @Nested
    class InsertIfAbsent {

        @Test
        void should_add_one_entry_per_user_and_return_the_count_when_the_tweet_is_new() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();

            int added = feedEntryRepository.insertIfAbsent(new UUID[]{first, second}, tweetId, authorId, TWEET_CREATED_AT);

            assertThat(added).isEqualTo(2);
            assertThat(entriesOf(first)).hasSize(1);
            assertThat(entriesOf(second)).hasSize(1);
        }

        @Test
        void should_store_the_tweet_the_author_and_the_exact_microsecond_time_when_an_entry_is_added() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();

            feedEntryRepository.insertIfAbsent(new UUID[]{userId}, tweetId, authorId, TWEET_CREATED_AT);
            entityManager.clear();

            FeedEntry stored = entriesOf(userId).getFirst();
            assertThat(stored.getTweetId()).isEqualTo(tweetId);
            assertThat(stored.getAuthorId()).isEqualTo(authorId);
            assertThat(stored.getTweetCreatedAt()).isEqualTo(TWEET_CREATED_AT);
        }

        @Test
        void should_add_nothing_and_return_zero_when_the_same_insert_is_repeated() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID userId = TestIds.userId();
            feedEntryRepository.insertIfAbsent(new UUID[]{userId}, tweetId, authorId, TWEET_CREATED_AT);

            int added = feedEntryRepository.insertIfAbsent(new UUID[]{userId}, tweetId, authorId, TWEET_CREATED_AT);

            assertThat(added).isZero();
            assertThat(entriesOf(userId)).hasSize(1);
        }

        @Test
        void should_add_only_the_missing_users_when_some_already_have_the_entry() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID existing = TestIds.userId();
            UUID missing = TestIds.userId();
            feedEntryRepository.insertIfAbsent(new UUID[]{existing}, tweetId, authorId, TWEET_CREATED_AT);

            int added = feedEntryRepository.insertIfAbsent(new UUID[]{existing, missing}, tweetId, authorId, TWEET_CREATED_AT);

            assertThat(added).isEqualTo(1);
            assertThat(entriesOf(missing)).hasSize(1);
        }

        @Test
        void should_add_one_entry_when_a_user_is_listed_twice() {
            UUID userId = TestIds.userId();

            int added = feedEntryRepository.insertIfAbsent(
                    new UUID[]{userId, userId}, TestIds.tweetId(), TestIds.userId(), TWEET_CREATED_AT);

            assertThat(added).isEqualTo(1);
        }

        @Test
        void should_add_a_thousand_entries_in_one_statement_when_a_full_follower_page_is_inserted() {
            UUID[] userIds = Stream
                    .generate(TestIds::userId)
                    .limit(1000)
                    .toArray(UUID[]::new);

            int added = feedEntryRepository.insertIfAbsent(userIds, TestIds.tweetId(), TestIds.userId(), TWEET_CREATED_AT);

            assertThat(added).isEqualTo(1000);
        }

        @Test
        void should_keep_the_same_tweet_in_other_feeds_when_it_is_added_to_a_new_one() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();

            feedEntryRepository.insertIfAbsent(new UUID[]{first}, tweetId, authorId, TWEET_CREATED_AT);
            feedEntryRepository.insertIfAbsent(new UUID[]{second}, tweetId, authorId, TWEET_CREATED_AT);

            assertThat(entriesOf(first)).hasSize(1);
            assertThat(entriesOf(second)).hasSize(1);
        }
    }

    @Nested
    class KeysetPages {

        @Test
        void should_order_entries_newest_first_and_break_ties_by_tweet_id_descending() {
            UUID userId = TestIds.userId();
            UUID oldest = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            UUID tiedLowId = entry(userId, LOW_ID, TWEET_CREATED_AT.plusSeconds(1));
            UUID tiedHighId = entry(userId, HIGH_ID, TWEET_CREATED_AT.plusSeconds(1));
            UUID newest = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.plusSeconds(2));

            List<FeedEntry> page = feedEntryRepository.findFirstPage(userId, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(newest, tiedHighId, tiedLowId, oldest);
        }

        @Test
        void should_return_only_the_entries_of_the_given_user() {
            UUID userId = TestIds.userId();
            UUID mine = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            entry(TestIds.userId(), TestIds.tweetId(), TWEET_CREATED_AT);

            assertThat(tweetIdsOf(feedEntryRepository.findFirstPage(userId, PageRequest.of(0, 10))))
                    .containsExactly(mine);
        }

        @Test
        void should_return_the_limit_when_there_are_more_entries() {
            UUID userId = TestIds.userId();
            entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.plusSeconds(1));

            assertThat(feedEntryRepository.findFirstPage(userId, PageRequest.of(0, 1))).hasSize(1);
        }

        @Test
        void should_return_nothing_when_the_user_has_no_entries() {
            assertThat(feedEntryRepository.findFirstPage(TestIds.userId(), PageRequest.of(0, 10))).isEmpty();
        }

        @Test
        void should_return_only_older_entries_when_the_page_starts_after_a_position() {
            UUID userId = TestIds.userId();
            UUID older = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            UUID boundary = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.plusSeconds(1));
            entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.plusSeconds(2));

            List<FeedEntry> page = feedEntryRepository.findPageAfter(
                    userId, TWEET_CREATED_AT.plusSeconds(1), boundary, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(older);
        }

        @Test
        void should_keep_the_lower_tweet_ids_at_the_boundary_time_when_the_page_starts_after_a_tied_entry() {
            UUID userId = TestIds.userId();
            entry(userId, HIGH_ID, TWEET_CREATED_AT);
            UUID tiedLowId = entry(userId, LOW_ID, TWEET_CREATED_AT);

            List<FeedEntry> page = feedEntryRepository.findPageAfter(
                    userId, TWEET_CREATED_AT, HIGH_ID, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(tiedLowId);
        }

        @Test
        void should_cover_every_entry_exactly_once_when_every_entry_shares_one_timestamp() {
            UUID userId = TestIds.userId();
            List<UUID> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(entry(userId, TestIds.tweetId(), TWEET_CREATED_AT));
            }

            List<UUID> seen = new ArrayList<>();
            List<FeedEntry> page = feedEntryRepository.findFirstPage(userId, PageRequest.of(0, 3));
            while (!page.isEmpty()) {
                seen.addAll(tweetIdsOf(page));
                FeedEntry last = page.getLast();
                page = feedEntryRepository.findPageAfter(
                        userId, last.getTweetCreatedAt(), last.getTweetId(), PageRequest.of(0, 3));
            }

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_order_the_primary_key_by_user_then_time_then_tweet_so_it_serves_the_page_query() {
            List<String> keyColumns = jdbcTemplate.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = 'feed_entries'::regclass AND i.indisprimary
                    ORDER BY array_position(i.indkey::int2[], a.attnum)
                    """, String.class);

            assertThat(keyColumns).containsExactly("user_id", "tweet_created_at", "tweet_id");
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_the_tweet_from_every_feed_and_return_the_count() {
            UUID tweetId = TestIds.tweetId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();
            entry(first, tweetId, TWEET_CREATED_AT);
            entry(second, tweetId, TWEET_CREATED_AT);

            int removed = feedEntryRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(2);
            assertThat(entriesOf(first)).isEmpty();
            assertThat(entriesOf(second)).isEmpty();
        }

        @Test
        void should_leave_other_tweets_untouched() {
            UUID userId = TestIds.userId();
            UUID deleted = TestIds.tweetId();
            UUID kept = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            entry(userId, deleted, TWEET_CREATED_AT.plusSeconds(1));

            feedEntryRepository.deleteByTweetId(deleted);

            assertThat(tweetIdsOf(entriesOf(userId))).containsExactly(kept);
        }

        @Test
        void should_return_zero_when_the_tweet_is_in_no_feed() {
            assertThat(feedEntryRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_return_zero_the_second_time_when_the_same_tweet_is_deleted_twice() {
            UUID tweetId = TestIds.tweetId();
            entry(TestIds.userId(), tweetId, TWEET_CREATED_AT);
            feedEntryRepository.deleteByTweetId(tweetId);

            assertThat(feedEntryRepository.deleteByTweetId(tweetId)).isZero();
        }
    }

    @Nested
    class DeleteByUnfollow {

        @Test
        void should_remove_the_authors_entries_up_to_and_including_the_occurred_at_time() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            entryBy(followerId, authorId, TWEET_CREATED_AT);
            entryBy(followerId, authorId, TWEET_CREATED_AT.plusSeconds(1));

            int removed = feedEntryRepository.deleteByUnfollow(followerId, authorId, TWEET_CREATED_AT.plusSeconds(1));

            assertThat(removed).isEqualTo(2);
            assertThat(entriesOf(followerId)).isEmpty();
        }

        @Test
        void should_keep_the_authors_entries_after_the_occurred_at_time() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            entryBy(followerId, authorId, TWEET_CREATED_AT);
            UUID after = entryBy(followerId, authorId, TWEET_CREATED_AT.plusNanos(1_000));

            feedEntryRepository.deleteByUnfollow(followerId, authorId, TWEET_CREATED_AT);

            assertThat(tweetIdsOf(entriesOf(followerId))).containsExactly(after);
        }

        @Test
        void should_keep_the_entries_of_other_authors() {
            UUID followerId = TestIds.userId();
            UUID authorId = TestIds.userId();
            entryBy(followerId, authorId, TWEET_CREATED_AT);
            UUID otherAuthors = entryBy(followerId, TestIds.userId(), TWEET_CREATED_AT);

            feedEntryRepository.deleteByUnfollow(followerId, authorId, TWEET_CREATED_AT.plusSeconds(60));

            assertThat(tweetIdsOf(entriesOf(followerId))).containsExactly(otherAuthors);
        }

        @Test
        void should_keep_the_entries_of_other_users() {
            UUID followerId = TestIds.userId();
            UUID otherUserId = TestIds.userId();
            UUID authorId = TestIds.userId();
            entryBy(followerId, authorId, TWEET_CREATED_AT);
            UUID otherUsers = entryBy(otherUserId, authorId, TWEET_CREATED_AT);

            feedEntryRepository.deleteByUnfollow(followerId, authorId, TWEET_CREATED_AT.plusSeconds(60));

            assertThat(tweetIdsOf(entriesOf(otherUserId))).containsExactly(otherUsers);
        }

        @Test
        void should_return_zero_when_there_is_nothing_to_remove() {
            assertThat(feedEntryRepository.deleteByUnfollow(TestIds.userId(), TestIds.userId(), TWEET_CREATED_AT))
                    .isZero();
        }
    }

    @Nested
    class DeleteOlderThan {

        @Test
        void should_remove_at_most_the_batch_size_when_more_old_entries_exist() {
            UUID userId = TestIds.userId();
            for (int index = 0; index < 7; index++) {
                entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.minusSeconds(index + 1));
            }

            int removed = feedEntryRepository.deleteOlderThan(TWEET_CREATED_AT, 3);

            assertThat(removed).isEqualTo(3);
            assertThat(entriesOf(userId)).hasSize(4);
        }

        @Test
        void should_remove_every_old_entry_when_the_batches_are_repeated_until_one_is_short() {
            UUID userId = TestIds.userId();
            for (int index = 0; index < 7; index++) {
                entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.minusSeconds(index + 1));
            }

            List<Integer> batches = new ArrayList<>();
            int removed;
            do {
                removed = feedEntryRepository.deleteOlderThan(TWEET_CREATED_AT, 3);
                batches.add(removed);
            } while (removed == 3);

            assertThat(batches).containsExactly(3, 3, 1);
            assertThat(entriesOf(userId)).isEmpty();
        }

        @Test
        void should_keep_entries_at_or_after_the_cutoff() {
            UUID userId = TestIds.userId();
            UUID atCutoff = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT);
            UUID newer = entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.plusSeconds(1));
            entry(userId, TestIds.tweetId(), TWEET_CREATED_AT.minusNanos(1_000));

            feedEntryRepository.deleteOlderThan(TWEET_CREATED_AT, 10);

            assertThat(tweetIdsOf(entriesOf(userId))).containsExactlyInAnyOrder(atCutoff, newer);
        }

        @Test
        void should_return_zero_when_nothing_is_older_than_the_cutoff() {
            entry(TestIds.userId(), TestIds.tweetId(), TWEET_CREATED_AT);

            assertThat(feedEntryRepository.deleteOlderThan(TWEET_CREATED_AT.minusSeconds(60), 10)).isZero();
        }
    }

    private UUID entry(UUID userId, UUID tweetId, Instant tweetCreatedAt) {
        return entryBy(userId, tweetId, TestIds.userId(), tweetCreatedAt);
    }

    private UUID entryBy(UUID userId, UUID authorId, Instant tweetCreatedAt) {
        return entryBy(userId, TestIds.tweetId(), authorId, tweetCreatedAt);
    }

    private UUID entryBy(UUID userId, UUID tweetId, UUID authorId, Instant tweetCreatedAt) {
        feedEntryRepository.insertIfAbsent(new UUID[]{userId}, tweetId, authorId, tweetCreatedAt);

        return tweetId;
    }

    private List<FeedEntry> entriesOf(UUID userId) {
        return feedEntryRepository.findFirstPage(userId, PageRequest.of(0, 100));
    }

    private List<UUID> tweetIdsOf(List<FeedEntry> entries) {
        return entries
                .stream()
                .map(FeedEntry::getTweetId)
                .toList();
    }
}
