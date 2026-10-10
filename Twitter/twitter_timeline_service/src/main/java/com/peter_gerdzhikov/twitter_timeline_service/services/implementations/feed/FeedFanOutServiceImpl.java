package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedFanOutService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowerLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.Durations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FeedFanOutServiceImpl implements FeedFanOutService {

    private final Clock clock;

    private final Duration retention;

    private final FeedEntryRepository feedEntryRepository;

    private final FollowerLookupService followerLookupService;

    private final EventValidationService eventValidationService;

    public FeedFanOutServiceImpl(
            Clock clock,
            FeedEntryRepository feedEntryRepository,
            FollowerLookupService followerLookupService,
            EventValidationService eventValidationService,
            @Value("${app.feed.retention}") Duration retention
    ) {
        this.clock = clock;
        this.feedEntryRepository = feedEntryRepository;
        this.followerLookupService = followerLookupService;
        this.eventValidationService = eventValidationService;
        this.retention = Durations.requirePositive(retention, "app.feed.retention");
    }

    @Override
    public void fanOut(TweetCreatedEventDTO event) {
        eventValidationService.validate(event, "tweet.created event " + event.getEventId() + " for tweet " + event.getTweetId());

        // A replay (a new consumer group, expired offsets, a dead-letter retry) brings old tweets back. The cleanup
        // job deletes any entry this old anyway, so adding it would only cost a follower lookup per tweet.
        if (isOlderThanRetention(event)) {
            log.info("Skipped tweet {} of author {}: it is older than the feed retention.", event.getTweetId(), event.getAuthorId());

            return;
        }

        // Microseconds are what Postgres stores, and the page cursor is built from this value.
        Instant tweetCreatedAt = event.getCreatedAt().truncatedTo(ChronoUnit.MICROS);
        AtomicInteger added = new AtomicInteger(addTo(List.of(event.getAuthorId()), event, tweetCreatedAt));

        followerLookupService
                .forEachFollowerPage(event.getAuthorId(), followerIds -> added.addAndGet(addTo(followerIds, event, tweetCreatedAt)));

        log.info("Fanned out tweet {} of author {} to {} feeds.", event.getTweetId(), event.getAuthorId(), added.get());
    }

    private boolean isOlderThanRetention(TweetCreatedEventDTO event) {
        return event.getCreatedAt().isBefore(clock.instant().minus(retention));
    }

    private int addTo(List<UUID> userIds, TweetCreatedEventDTO event, Instant tweetCreatedAt) {
        return feedEntryRepository.insertIfAbsent(
                userIds.toArray(UUID[]::new), event.getTweetId(), event.getAuthorId(), tweetCreatedAt);
    }
}
