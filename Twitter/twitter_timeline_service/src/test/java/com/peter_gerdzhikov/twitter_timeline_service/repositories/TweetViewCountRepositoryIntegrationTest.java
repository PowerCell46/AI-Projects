package com.peter_gerdzhikov.twitter_timeline_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_timeline_service.entities.TweetViewCount;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TweetViewCountRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TweetViewCountRepository tweetViewCountRepository;

    @Nested
    class IncrementAll {

        @Test
        void should_create_the_counter_at_one_when_the_tweet_has_none() {
            UUID tweetId = TestIds.tweetId();

            int touched = tweetViewCountRepository.incrementAll(new UUID[]{tweetId});

            assertThat(touched).isEqualTo(1);
            assertThat(viewsOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_add_one_when_the_counter_exists() {
            UUID tweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{tweetId});

            tweetViewCountRepository.incrementAll(new UUID[]{tweetId});

            assertThat(viewsOf(tweetId)).isEqualTo(2);
        }

        @Test
        void should_add_one_to_each_tweet_when_the_batch_holds_new_and_existing_counters() {
            UUID existingTweetId = TestIds.tweetId();
            UUID newTweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{existingTweetId});

            int touched = tweetViewCountRepository.incrementAll(new UUID[]{existingTweetId, newTweetId});

            assertThat(touched).isEqualTo(2);
            assertThat(viewsOf(existingTweetId)).isEqualTo(2);
            assertThat(viewsOf(newTweetId)).isEqualTo(1);
        }

        @Test
        void should_leave_the_counters_of_other_tweets_alone_when_a_tweet_is_incremented() {
            UUID otherTweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{otherTweetId});

            tweetViewCountRepository.incrementAll(new UUID[]{TestIds.tweetId()});

            assertThat(viewsOf(otherTweetId)).isEqualTo(1);
        }

        @Test
        void should_do_nothing_when_the_batch_is_empty() {
            assertThat(tweetViewCountRepository.incrementAll(new UUID[0])).isZero();
        }

        @Test
        void should_refuse_a_negative_count_when_a_row_is_written_by_hand() {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO tweet_view_counts (tweet_id, views) VALUES (?, -1)", TestIds.tweetId()
            )).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class FindAllById {

        @Test
        void should_return_only_the_counters_that_exist_when_some_ids_are_unknown() {
            UUID countedTweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{countedTweetId});

            List<TweetViewCount> found = tweetViewCountRepository.findAllById(List.of(countedTweetId, TestIds.tweetId()));

            assertThat(found).hasSize(1);
            assertThat(found.getFirst().getTweetId()).isEqualTo(countedTweetId);
            assertThat(found.getFirst().getViews()).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_the_counter_and_return_one_when_the_tweet_has_one() {
            UUID tweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{tweetId});

            int removed = tweetViewCountRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(1);
            assertThat(tweetViewCountRepository.existsById(tweetId)).isFalse();
        }

        @Test
        void should_return_zero_when_the_tweet_has_no_counter() {
            assertThat(tweetViewCountRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_leave_the_counters_of_other_tweets_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            tweetViewCountRepository.incrementAll(new UUID[]{deletedTweetId, keptTweetId});

            tweetViewCountRepository.deleteByTweetId(deletedTweetId);

            assertThat(viewsOf(keptTweetId)).isEqualTo(1);
        }
    }

    private long viewsOf(UUID tweetId) {
        return tweetViewCountRepository
                .findById(tweetId)
                .orElseThrow()
                .getViews();
    }
}
