package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;

@ExtendWith(MockitoExtension.class)
class FeedRetentionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private static final Duration RETENTION = Duration.ofDays(7);

    private static final int BATCH_SIZE = 100;

    private static final Instant CUTOFF = NOW.minus(RETENTION);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private FeedEntryRepository feedEntryRepository;

    private FeedRetentionServiceImpl feedRetentionService;

    @BeforeEach
    void setUp() {
        feedRetentionService = new FeedRetentionServiceImpl(clock, feedEntryRepository, RETENTION, BATCH_SIZE);
    }

    @Nested
    class Construction {

        @Test
        void should_refuse_to_start_when_the_retention_is_zero() {
            assertThatThrownBy(() -> new FeedRetentionServiceImpl(clock, feedEntryRepository, Duration.ZERO, BATCH_SIZE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.retention must be positive");
        }

        @Test
        void should_refuse_to_start_when_the_retention_is_negative() {
            assertThatThrownBy(() -> new FeedRetentionServiceImpl(clock, feedEntryRepository, Duration.ofDays(-1), BATCH_SIZE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.retention must be positive");
        }

        @Test
        void should_refuse_to_start_when_the_batch_size_is_zero() {
            assertThatThrownBy(() -> new FeedRetentionServiceImpl(clock, feedEntryRepository, RETENTION, 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.feed.cleanup.batch-size must be positive");
        }
    }

    @Nested
    class DeleteExpiredEntries {

        @Test
        void should_stop_after_one_batch_when_it_removes_fewer_than_the_batch_size() {
            when(feedEntryRepository.deleteOlderThan(CUTOFF, BATCH_SIZE)).thenReturn(BATCH_SIZE - 1);

            int deleted = feedRetentionService.deleteExpiredEntries();

            assertThat(deleted).isEqualTo(BATCH_SIZE - 1);
            verify(feedEntryRepository).deleteOlderThan(CUTOFF, BATCH_SIZE);
            verifyNoMoreInteractions(feedEntryRepository);
        }

        @Test
        void should_keep_going_while_a_batch_is_full_and_sum_the_batches() {
            when(feedEntryRepository.deleteOlderThan(CUTOFF, BATCH_SIZE)).thenReturn(BATCH_SIZE, BATCH_SIZE, 30);

            int deleted = feedRetentionService.deleteExpiredEntries();

            assertThat(deleted).isEqualTo(2 * BATCH_SIZE + 30);
            verify(feedEntryRepository, times(3)).deleteOlderThan(CUTOFF, BATCH_SIZE);
        }

        @Test
        void should_run_one_more_batch_when_the_last_batch_was_exactly_full() {
            when(feedEntryRepository.deleteOlderThan(CUTOFF, BATCH_SIZE)).thenReturn(BATCH_SIZE, 0);

            int deleted = feedRetentionService.deleteExpiredEntries();

            assertThat(deleted).isEqualTo(BATCH_SIZE);
            verify(feedEntryRepository, times(2)).deleteOlderThan(CUTOFF, BATCH_SIZE);
        }

        @Test
        void should_use_one_cutoff_for_every_batch() {
            when(feedEntryRepository.deleteOlderThan(any(), anyInt())).thenReturn(BATCH_SIZE, 0);

            feedRetentionService.deleteExpiredEntries();

            verify(feedEntryRepository, times(2)).deleteOlderThan(eq(CUTOFF), eq(BATCH_SIZE));
            verify(feedEntryRepository, never()).deleteOlderThan(eq(NOW), anyInt());
        }
    }
}
