package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedRetentionService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.Durations;

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
        this.retention = Durations.requirePositive(retention, "app.feed.retention");
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

    private int requirePositive(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException("app.feed.cleanup.batch-size must be positive, but was " + batchSize + ".");
        }

        return batchSize;
    }
}
