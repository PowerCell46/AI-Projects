package com.peter_gerdzhikov.twitter_timeline_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_timeline_service.entities.TweetView;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TweetViewRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TweetViewRepository tweetViewRepository;

    @Nested
    class InsertIfAbsent {

        @Test
        void should_return_the_tweet_and_store_one_row_when_the_viewer_is_new_to_it() {
            UUID viewerId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();

            List<UUID> added = tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{tweetId});

            assertThat(added).containsExactly(tweetId);
            assertThat(viewersOf(tweetId)).containsExactly(viewerId);
        }

        @Test
        void should_return_nothing_and_keep_one_row_when_the_same_pair_is_inserted_again() {
            UUID viewerId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{tweetId});

            List<UUID> added = tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{tweetId});

            assertThat(added).isEmpty();
            assertThat(viewersOf(tweetId)).containsExactly(viewerId);
        }

        @Test
        void should_return_only_the_unseen_tweets_when_the_batch_mixes_seen_and_unseen_ones() {
            UUID viewerId = TestIds.userId();
            UUID seenTweetId = TestIds.tweetId();
            UUID unseenTweetId = TestIds.tweetId();
            tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{seenTweetId});

            List<UUID> added = tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{seenTweetId, unseenTweetId});

            assertThat(added).containsExactly(unseenTweetId);
        }

        @Test
        void should_store_every_tweet_of_the_batch_when_all_are_new() {
            UUID viewerId = TestIds.userId();
            List<UUID> tweetIds = List.of(TestIds.tweetId(), TestIds.tweetId(), TestIds.tweetId());

            List<UUID> added = tweetViewRepository.insertIfAbsent(viewerId, tweetIds.toArray(UUID[]::new));

            assertThat(added).containsExactlyInAnyOrderElementsOf(tweetIds);
            tweetIds.forEach(tweetId -> assertThat(viewersOf(tweetId)).containsExactly(viewerId));
        }

        @Test
        void should_record_each_viewer_when_two_viewers_view_the_same_tweet() {
            UUID tweetId = TestIds.tweetId();
            UUID firstViewerId = TestIds.userId();
            UUID secondViewerId = TestIds.userId();

            tweetViewRepository.insertIfAbsent(firstViewerId, new UUID[]{tweetId});
            List<UUID> added = tweetViewRepository.insertIfAbsent(secondViewerId, new UUID[]{tweetId});

            assertThat(added).containsExactly(tweetId);
            assertThat(viewersOf(tweetId)).containsExactlyInAnyOrder(firstViewerId, secondViewerId);
        }

        @Test
        void should_return_the_ids_in_ascending_order_when_the_batch_comes_in_descending_order() {
            UUID viewerId = TestIds.userId();

            List<UUID> added = tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{HIGH_ID, LOW_ID});

            assertThat(added).containsExactly(LOW_ID, HIGH_ID);
        }

        @Test
        void should_return_nothing_when_the_batch_is_empty() {
            assertThat(tweetViewRepository.insertIfAbsent(TestIds.userId(), new UUID[0])).isEmpty();
        }

        @Test
        void should_key_the_table_by_tweet_then_viewer_so_the_delete_by_tweet_does_not_scan_it() {
            List<String> keyColumns = jdbcTemplate.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = 'tweet_views'::regclass AND i.indisprimary
                    ORDER BY array_position(i.indkey::int2[], a.attnum)
                    """, String.class);

            assertThat(keyColumns).containsExactly("tweet_id", "viewer_id");
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_every_viewer_of_the_tweet_and_return_their_number_when_the_tweet_has_views() {
            UUID tweetId = TestIds.tweetId();
            view(TestIds.userId(), tweetId);
            view(TestIds.userId(), tweetId);
            view(TestIds.userId(), tweetId);

            int removed = tweetViewRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(3);
            assertThat(viewersOf(tweetId)).isEmpty();
        }

        @Test
        void should_return_zero_when_the_tweet_has_no_views() {
            assertThat(tweetViewRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_leave_the_views_of_other_tweets_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            UUID viewerId = TestIds.userId();
            view(viewerId, deletedTweetId);
            view(viewerId, keptTweetId);

            tweetViewRepository.deleteByTweetId(deletedTweetId);

            assertThat(viewersOf(keptTweetId)).containsExactly(viewerId);
        }
    }

    private void view(UUID viewerId, UUID tweetId) {
        tweetViewRepository.insertIfAbsent(viewerId, new UUID[]{tweetId});
    }

    private List<UUID> viewersOf(UUID tweetId) {
        return tweetViewRepository
                .findAll()
                .stream()
                .filter(view -> view.getTweetId().equals(tweetId))
                .map(TweetView::getViewerId)
                .toList();
    }
}
