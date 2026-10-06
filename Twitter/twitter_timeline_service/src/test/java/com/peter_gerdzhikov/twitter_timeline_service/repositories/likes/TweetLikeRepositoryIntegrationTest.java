package com.peter_gerdzhikov.twitter_timeline_service.repositories.likes;

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

import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLike;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TweetLikeRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant LIKED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TweetLikeRepository tweetLikeRepository;

    @Nested
    class InsertIfAbsent {

        @Test
        void should_add_one_row_and_return_one_when_the_tweet_is_new_to_the_user() {
            UUID userId = TestIds.userId();

            int added = tweetLikeRepository.insertIfAbsent(userId, TestIds.tweetId(), TestIds.userId(), LIKED_AT);

            assertThat(added).isEqualTo(1);
            assertThat(likedBy(userId)).hasSize(1);
        }

        @Test
        void should_store_the_tweet_the_author_and_the_exact_microsecond_time_when_a_row_is_added() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();

            tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, LIKED_AT);
            entityManager.clear();

            TweetLike stored = likedBy(userId).getFirst();
            assertThat(stored.getTweetId()).isEqualTo(tweetId);
            assertThat(stored.getAuthorId()).isEqualTo(authorId);
            assertThat(stored.getLikedAt()).isEqualTo(LIKED_AT);
        }

        @Test
        void should_add_nothing_and_return_zero_when_the_same_pair_is_inserted_again() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, LIKED_AT);

            int added = tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, LIKED_AT.plusSeconds(60));

            assertThat(added).isZero();
            assertThat(likedBy(userId)).hasSize(1);
        }

        @Test
        void should_keep_the_original_liked_at_when_the_same_pair_is_inserted_later() {
            UUID userId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, LIKED_AT);

            tweetLikeRepository.insertIfAbsent(userId, tweetId, authorId, LIKED_AT.plusSeconds(60));
            entityManager.clear();

            assertThat(likedBy(userId).getFirst().getLikedAt()).isEqualTo(LIKED_AT);
        }

        @Test
        void should_add_the_tweet_for_each_user_when_different_users_like_it() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();

            tweetLikeRepository.insertIfAbsent(first, tweetId, authorId, LIKED_AT);
            tweetLikeRepository.insertIfAbsent(second, tweetId, authorId, LIKED_AT);

            assertThat(likedBy(first)).hasSize(1);
            assertThat(likedBy(second)).hasSize(1);
        }
    }

    @Nested
    class KeysetPages {

        @Test
        void should_order_rows_most_recently_liked_first_and_break_ties_by_tweet_id_descending() {
            UUID userId = TestIds.userId();
            UUID oldest = like(userId, TestIds.tweetId(), LIKED_AT);
            UUID tiedLowId = like(userId, LOW_ID, LIKED_AT.plusSeconds(1));
            UUID tiedHighId = like(userId, HIGH_ID, LIKED_AT.plusSeconds(1));
            UUID newest = like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(2));

            List<TweetLike> page = tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(newest, tiedHighId, tiedLowId, oldest);
        }

        @Test
        void should_return_only_the_rows_of_the_given_user() {
            UUID userId = TestIds.userId();
            UUID mine = like(userId, TestIds.tweetId(), LIKED_AT);
            like(TestIds.userId(), TestIds.tweetId(), LIKED_AT);

            assertThat(tweetIdsOf(tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 10))))
                    .containsExactly(mine);
        }

        @Test
        void should_return_the_limit_when_there_are_more_rows() {
            UUID userId = TestIds.userId();
            like(userId, TestIds.tweetId(), LIKED_AT);
            like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(1));

            assertThat(tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 1))).hasSize(1);
        }

        @Test
        void should_return_nothing_when_the_user_liked_nothing() {
            assertThat(tweetLikeRepository.findFirstPage(TestIds.userId(), PageRequest.of(0, 10))).isEmpty();
        }

        @Test
        void should_return_only_older_rows_when_the_page_starts_after_a_position() {
            UUID userId = TestIds.userId();
            UUID older = like(userId, TestIds.tweetId(), LIKED_AT);
            UUID boundary = like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(1));
            like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(2));

            List<TweetLike> page = tweetLikeRepository.findPageAfter(
                    userId, LIKED_AT.plusSeconds(1), boundary, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(older);
        }

        @Test
        void should_keep_the_lower_tweet_ids_at_the_boundary_time_when_the_page_starts_after_a_tied_row() {
            UUID userId = TestIds.userId();
            like(userId, HIGH_ID, LIKED_AT);
            UUID tiedLowId = like(userId, LOW_ID, LIKED_AT);

            List<TweetLike> page = tweetLikeRepository.findPageAfter(
                    userId, LIKED_AT, HIGH_ID, PageRequest.of(0, 10));

            assertThat(tweetIdsOf(page)).containsExactly(tiedLowId);
        }

        @Test
        void should_cover_every_row_exactly_once_when_every_row_shares_one_timestamp() {
            UUID userId = TestIds.userId();
            List<UUID> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(like(userId, TestIds.tweetId(), LIKED_AT));
            }

            List<UUID> seen = new ArrayList<>();
            List<TweetLike> page = tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 3));
            while (!page.isEmpty()) {
                seen.addAll(tweetIdsOf(page));
                TweetLike last = page.getLast();
                page = tweetLikeRepository.findPageAfter(
                        userId, last.getLikedAt(), last.getTweetId(), PageRequest.of(0, 3));
            }

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_index_the_list_by_user_then_liked_time_then_tweet_so_it_serves_the_page_query() {
            List<String> indexColumns = jdbcTemplate.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_class c ON c.oid = i.indexrelid
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = 'tweet_likes'::regclass AND c.relname = 'ix_tweet_likes_user_liked'
                    ORDER BY array_position(i.indkey::int2[], a.attnum)
                    """, String.class);

            assertThat(indexColumns).containsExactly("user_id", "liked_at", "tweet_id");
        }

        @Test
        void should_key_the_table_by_user_then_tweet() {
            List<String> keyColumns = jdbcTemplate.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = 'tweet_likes'::regclass AND i.indisprimary
                    ORDER BY array_position(i.indkey::int2[], a.attnum)
                    """, String.class);

            assertThat(keyColumns).containsExactly("user_id", "tweet_id");
        }

        @Test
        void should_index_the_tweet_so_the_delete_by_tweet_does_not_scan_the_table() {
            Integer indexes = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_indexes
                    WHERE tablename = 'tweet_likes' AND indexname = 'ix_tweet_likes_tweet'
                    """, Integer.class);

            assertThat(indexes).isEqualTo(1);
        }
    }

    @Nested
    class FindLikedTweetIds {

        @Test
        void should_return_only_the_requested_tweets_the_user_liked() {
            UUID userId = TestIds.userId();
            UUID liked = like(userId, TestIds.tweetId(), LIKED_AT);
            UUID notRequested = like(userId, TestIds.tweetId(), LIKED_AT);
            UUID notLiked = TestIds.tweetId();

            List<UUID> found = tweetLikeRepository.findLikedTweetIds(userId, List.of(liked, notLiked));

            assertThat(found).containsExactly(liked).doesNotContain(notRequested);
        }

        @Test
        void should_not_return_a_tweet_only_other_users_liked() {
            UUID tweetId = like(TestIds.userId(), TestIds.tweetId(), LIKED_AT);

            assertThat(tweetLikeRepository.findLikedTweetIds(TestIds.userId(), List.of(tweetId))).isEmpty();
        }

        @Test
        void should_return_each_tweet_once_when_other_users_liked_it_too() {
            UUID userId = TestIds.userId();
            UUID tweetId = like(userId, TestIds.tweetId(), LIKED_AT);
            like(TestIds.userId(), tweetId, LIKED_AT);

            assertThat(tweetLikeRepository.findLikedTweetIds(userId, List.of(tweetId))).containsExactly(tweetId);
        }

        @Test
        void should_return_nothing_when_the_user_liked_none_of_the_tweets() {
            assertThat(tweetLikeRepository.findLikedTweetIds(TestIds.userId(), List.of(TestIds.tweetId()))).isEmpty();
        }
    }

    @Nested
    class DeleteByUserAndTweet {

        @Test
        void should_remove_the_row_and_return_one_when_the_tweet_is_liked() {
            UUID userId = TestIds.userId();
            UUID tweetId = like(userId, TestIds.tweetId(), LIKED_AT);

            int removed = tweetLikeRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(removed).isEqualTo(1);
            assertThat(likedBy(userId)).isEmpty();
        }

        @Test
        void should_return_zero_when_the_tweet_is_not_liked() {
            assertThat(tweetLikeRepository.deleteByUserAndTweet(TestIds.userId(), TestIds.tweetId())).isZero();
        }

        @Test
        void should_leave_the_same_tweet_liked_by_other_users() {
            UUID tweetId = TestIds.tweetId();
            UUID userId = TestIds.userId();
            UUID otherUserId = TestIds.userId();
            like(userId, tweetId, LIKED_AT);
            like(otherUserId, tweetId, LIKED_AT);

            tweetLikeRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(tweetIdsOf(likedBy(otherUserId))).containsExactly(tweetId);
        }

        @Test
        void should_leave_the_users_other_tweets() {
            UUID userId = TestIds.userId();
            UUID kept = like(userId, TestIds.tweetId(), LIKED_AT);
            UUID removed = like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(1));

            tweetLikeRepository.deleteByUserAndTweet(userId, removed);

            assertThat(tweetIdsOf(likedBy(userId))).containsExactly(kept);
        }

        @Test
        void should_return_zero_the_second_time_when_the_same_tweet_is_unliked_twice() {
            UUID userId = TestIds.userId();
            UUID tweetId = like(userId, TestIds.tweetId(), LIKED_AT);
            tweetLikeRepository.deleteByUserAndTweet(userId, tweetId);

            assertThat(tweetLikeRepository.deleteByUserAndTweet(userId, tweetId)).isZero();
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_the_tweet_from_every_liked_list_and_return_the_count() {
            UUID tweetId = TestIds.tweetId();
            UUID first = TestIds.userId();
            UUID second = TestIds.userId();
            like(first, tweetId, LIKED_AT);
            like(second, tweetId, LIKED_AT);

            int removed = tweetLikeRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(2);
            assertThat(likedBy(first)).isEmpty();
            assertThat(likedBy(second)).isEmpty();
        }

        @Test
        void should_leave_other_tweets_untouched() {
            UUID userId = TestIds.userId();
            UUID deleted = like(userId, TestIds.tweetId(), LIKED_AT.plusSeconds(1));
            UUID kept = like(userId, TestIds.tweetId(), LIKED_AT);

            tweetLikeRepository.deleteByTweetId(deleted);

            assertThat(tweetIdsOf(likedBy(userId))).containsExactly(kept);
        }

        @Test
        void should_return_zero_when_the_tweet_is_in_no_liked_list() {
            assertThat(tweetLikeRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_return_zero_the_second_time_when_the_same_tweet_is_deleted_twice() {
            UUID tweetId = like(TestIds.userId(), TestIds.tweetId(), LIKED_AT);
            tweetLikeRepository.deleteByTweetId(tweetId);

            assertThat(tweetLikeRepository.deleteByTweetId(tweetId)).isZero();
        }
    }

    private UUID like(UUID userId, UUID tweetId, Instant likedAt) {
        tweetLikeRepository.insertIfAbsent(userId, tweetId, TestIds.userId(), likedAt);

        return tweetId;
    }

    private List<TweetLike> likedBy(UUID userId) {
        return tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 100));
    }

    private List<UUID> tweetIdsOf(List<TweetLike> tweetLikes) {
        return tweetLikes
                .stream()
                .map(TweetLike::getTweetId)
                .toList();
    }
}
