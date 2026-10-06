package com.peter_gerdzhikov.twitter_timeline_service.repositories.likes;

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

import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLikeCount;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractPostgresIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TweetLikeCountRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TweetLikeCountRepository tweetLikeCountRepository;

    @Nested
    class Increment {

        @Test
        void should_create_the_counter_at_one_when_the_tweet_has_none() {
            UUID tweetId = TestIds.tweetId();

            int touched = tweetLikeCountRepository.increment(tweetId);

            assertThat(touched).isEqualTo(1);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_add_one_when_the_counter_exists() {
            UUID tweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(tweetId);

            tweetLikeCountRepository.increment(tweetId);

            assertThat(likesOf(tweetId)).isEqualTo(2);
        }

        @Test
        void should_leave_the_counters_of_other_tweets_alone_when_a_tweet_is_incremented() {
            UUID otherTweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(otherTweetId);

            tweetLikeCountRepository.increment(TestIds.tweetId());

            assertThat(likesOf(otherTweetId)).isEqualTo(1);
        }

        @Test
        void should_refuse_a_negative_count_when_a_row_is_written_by_hand() {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO tweet_like_counts (tweet_id, likes) VALUES (?, -1)", TestIds.tweetId()
            )).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Decrement {

        @Test
        void should_lower_the_counter_to_zero_when_the_tweet_has_one_like() {
            UUID tweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(tweetId);

            int touched = tweetLikeCountRepository.decrement(tweetId);

            assertThat(touched).isEqualTo(1);
            assertThat(likesOf(tweetId)).isZero();
        }

        @Test
        void should_touch_nothing_when_the_tweet_has_no_counter() {
            assertThat(tweetLikeCountRepository.decrement(TestIds.tweetId())).isZero();
        }

        @Test
        void should_refuse_to_go_below_zero_when_the_counter_is_already_zero() {
            UUID tweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(tweetId);
            tweetLikeCountRepository.decrement(tweetId);

            assertThatThrownBy(() -> tweetLikeCountRepository.decrement(tweetId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void should_leave_the_counters_of_other_tweets_alone_when_a_tweet_is_decremented() {
            UUID otherTweetId = TestIds.tweetId();
            UUID tweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(otherTweetId);
            tweetLikeCountRepository.increment(tweetId);

            tweetLikeCountRepository.decrement(tweetId);

            assertThat(likesOf(otherTweetId)).isEqualTo(1);
        }
    }

    @Nested
    class FindAllById {

        @Test
        void should_return_only_the_counters_that_exist_when_some_ids_are_unknown() {
            UUID countedTweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(countedTweetId);

            List<TweetLikeCount> found = tweetLikeCountRepository.findAllById(List.of(countedTweetId, TestIds.tweetId()));

            assertThat(found).hasSize(1);
            assertThat(found.getFirst().getTweetId()).isEqualTo(countedTweetId);
            assertThat(found.getFirst().getLikes()).isEqualTo(1);
        }
    }

    @Nested
    class DeleteByTweetId {

        @Test
        void should_remove_the_counter_and_return_one_when_the_tweet_has_one() {
            UUID tweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(tweetId);

            int removed = tweetLikeCountRepository.deleteByTweetId(tweetId);

            assertThat(removed).isEqualTo(1);
            assertThat(tweetLikeCountRepository.existsById(tweetId)).isFalse();
        }

        @Test
        void should_return_zero_when_the_tweet_has_no_counter() {
            assertThat(tweetLikeCountRepository.deleteByTweetId(TestIds.tweetId())).isZero();
        }

        @Test
        void should_leave_the_counters_of_other_tweets_when_a_tweet_is_deleted() {
            UUID deletedTweetId = TestIds.tweetId();
            UUID keptTweetId = TestIds.tweetId();
            tweetLikeCountRepository.increment(deletedTweetId);
            tweetLikeCountRepository.increment(keptTweetId);

            tweetLikeCountRepository.deleteByTweetId(deletedTweetId);

            assertThat(likesOf(keptTweetId)).isEqualTo(1);
        }
    }

    private long likesOf(UUID tweetId) {
        return tweetLikeCountRepository
                .findById(tweetId)
                .orElseThrow()
                .getLikes();
    }
}
