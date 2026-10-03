package com.peter_gerdzhikov.twitter_timeline_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class FeedRetentionIntegrationTest extends AbstractListenerIntegrationTest {

    private static final Duration RETENTION = Duration.ofDays(7);

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private FeedRetentionJob feedRetentionJob;

    @Value("${app.feed.cleanup.batch-size}")
    private int batchSize;

    @AfterEach
    void resetClock() {
        mutableClock.reset();
    }

    @Nested
    class Retention {

        private final UUID userId = TestIds.userId();

        private final UUID authorId = TestIds.userId();

        @Test
        void should_delete_entries_older_than_the_retention_across_several_batches_when_the_job_runs() {
            Instant tooOld = mutableClock.instant().minus(RETENTION).minus(Duration.ofHours(1));
            seedEntriesAt(tooOld, 2 * batchSize + 1);

            feedRetentionJob.deleteExpiredFeedEntries();

            assertThat(tweetIdsInFeedOf(userId)).isEmpty();
        }

        @Test
        void should_keep_the_newer_entries_when_the_job_runs() {
            Instant tooOld = mutableClock.instant().minus(RETENTION).minus(Duration.ofHours(1));
            Instant justInside = mutableClock.instant().minus(RETENTION).plus(Duration.ofHours(1));
            List<UUID> expiredTweetIds = seedEntriesAt(tooOld, batchSize + 1);
            List<UUID> keptTweetIds = seedEntriesAt(justInside, 3);

            feedRetentionJob.deleteExpiredFeedEntries();

            assertThat(tweetIdsInFeedOf(userId))
                    .containsExactlyInAnyOrderElementsOf(keptTweetIds)
                    .doesNotContainAnyElementsOf(expiredTweetIds);
        }

        @Test
        void should_keep_an_entry_exactly_at_the_cutoff_when_the_job_runs() {
            Instant atCutoff = mutableClock.instant().minus(RETENTION);
            List<UUID> keptTweetIds = seedEntriesAt(atCutoff, 1);

            feedRetentionJob.deleteExpiredFeedEntries();

            assertThat(tweetIdsInFeedOf(userId)).containsExactlyElementsOf(keptTweetIds);
        }

        @Test
        void should_delete_entries_that_age_past_the_retention_when_the_clock_moves_on() {
            Instant createdAt = mutableClock.instant();
            List<UUID> tweetIds = seedEntriesAt(createdAt, 2);
            feedRetentionJob.deleteExpiredFeedEntries();
            assertThat(tweetIdsInFeedOf(userId)).containsExactlyInAnyOrderElementsOf(tweetIds);

            mutableClock.advance(RETENTION.plus(Duration.ofSeconds(1)));
            feedRetentionJob.deleteExpiredFeedEntries();

            assertThat(tweetIdsInFeedOf(userId)).isEmpty();
        }

        @Test
        void should_delete_nothing_when_no_entry_is_old_enough() {
            List<UUID> tweetIds = seedEntriesAt(mutableClock.instant(), batchSize);

            feedRetentionJob.deleteExpiredFeedEntries();

            assertThat(tweetIdsInFeedOf(userId)).containsExactlyInAnyOrderElementsOf(tweetIds);
        }

        private List<UUID> seedEntriesAt(Instant tweetCreatedAt, int count) {
            return IntStream
                    .range(0, count)
                    .mapToObj(i -> seed(tweetCreatedAt))
                    .toList();
        }

        private UUID seed(Instant tweetCreatedAt) {
            UUID tweetId = TestIds.tweetId();
            seedEntry(userId, tweetId, authorId, tweetCreatedAt);

            return tweetId;
        }
    }
}
