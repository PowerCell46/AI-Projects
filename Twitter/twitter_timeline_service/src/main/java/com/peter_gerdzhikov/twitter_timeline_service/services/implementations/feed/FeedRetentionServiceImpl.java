package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedRetentionService;

@Service
public class FeedRetentionServiceImpl implements FeedRetentionService {

    private final Clock clock;

    private final int batchSize;

    private final Duration retention;

    private final FeedEntryRepository feedEntryRepository;

    public FeedRetentionServiceImpl(
            Clock clock,
            FeedEntryRepository feedEntryRepository,
            @Value("${app.feed.retention}") Duration retention,
            @Value("${app.feed.cleanup.batch-size}") int batchSize
    ) {
        this.clock = clock;
        this.feedEntryRepository = feedEntryRepository;
        this.retention = requirePositive(retention);
        this.batchSize = requirePositive(batchSize);
    }

    @Override
    public int deleteExpiredEntries() {
        Instant cutoff = clock.instant().minus(retention);
        int total = 0;
        int removed;

        do {
            removed = feedEntryRepository.deleteOlderThan(cutoff, batchSize);
            total += removed;
        } while (removed == batchSize);

        return total;
    }

    private Duration requirePositive(Duration retention) {
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalStateException("app.feed.retention must be positive, but was " + retention + ".");
        }

        return retention;
    }

    private int requirePositive(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException("app.feed.cleanup.batch-size must be positive, but was " + batchSize + ".");
        }

        return batchSize;
    }
}
