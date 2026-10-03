package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedFanOutService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FollowerLookupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFanOutServiceImpl implements FeedFanOutService {

    private final FeedEntryRepository feedEntryRepository;

    private final FollowerLookupService followerLookupService;

    private final EventValidationService eventValidationService;

    @Override
    public void fanOut(TweetCreatedEventDTO event) {
        eventValidationService.validate(event, "tweet.created event " + event.getEventId() + " for tweet " + event.getTweetId());

        // Microseconds are what Postgres stores, and the page cursor is built from this value.
        Instant tweetCreatedAt = event.getCreatedAt().truncatedTo(ChronoUnit.MICROS);
        AtomicInteger added = new AtomicInteger(addTo(List.of(event.getAuthorId()), event, tweetCreatedAt));

        followerLookupService.forEachFollowerPage(
                event.getAuthorId(),
                followerIds -> added.addAndGet(addTo(followerIds, event, tweetCreatedAt)));

        log.info("Fanned out tweet {} of author {} to {} feeds.", event.getTweetId(), event.getAuthorId(), added.get());
    }

    private int addTo(List<UUID> userIds, TweetCreatedEventDTO event, Instant tweetCreatedAt) {
        return feedEntryRepository.insertIfAbsent(
                userIds.toArray(UUID[]::new), event.getTweetId(), event.getAuthorId(), tweetCreatedAt);
    }
}
