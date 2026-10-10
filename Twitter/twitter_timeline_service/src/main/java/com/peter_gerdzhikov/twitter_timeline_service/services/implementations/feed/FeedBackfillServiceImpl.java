package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetSummaryClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedBackfillService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.Durations;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FeedBackfillServiceImpl implements FeedBackfillService {

    // The tweet service answers 400 to a by-author read above this.
    private static final int MAX_TWEETS_PER_AUTHOR_READ = 100;

    private final Clock clock;

    private final int backfillSize;

    private final Duration retention;

    private final TweetLookupService tweetLookupService;

    private final FeedEntryRepository feedEntryRepository;

    private final FollowLookupService followLookupService;

    private final EventValidationService eventValidationService;

    public FeedBackfillServiceImpl(
            Clock clock,
            FeedEntryRepository feedEntryRepository,
            TweetLookupService tweetLookupService,
            FollowLookupService followLookupService,
            EventValidationService eventValidationService,
            @Value("${app.feed.retention}") Duration retention,
            @Value("${app.feed.backfill-size}") int backfillSize
    ) {
        this.clock = clock;
        this.feedEntryRepository = feedEntryRepository;
        this.tweetLookupService = tweetLookupService;
        this.followLookupService = followLookupService;
        this.eventValidationService = eventValidationService;
        this.retention = Durations.requirePositive(retention, "app.feed.retention");
        this.backfillSize = requireAtMostTweetServiceLimit(requirePositive(backfillSize));
    }

    @Override
    public void backfill(UserFollowedEventDTO event) {
        eventValidationService.validate(event, "user.followed event " + event.getEventId() + " for follower " + event.getFollowerId());
        requireDifferentUsers(event);

        // Anchored to the clock, not to the event time: that time is not trusted, and an entry older than the
        // retention would only wait for the next cleanup.
        List<TweetSummaryClientDTO> tweets = tweetLookupService
                .findNewestByAuthor(event.getFolloweeId(), clock.instant().minus(retention), backfillSize)
                .stream()
                .limit(backfillSize)
                .toList();

        if (tweets.isEmpty()) {
            log.info("User {} followed {}, who has no tweets in the retention window.", event.getFollowerId(), event.getFolloweeId());

            return;
        }

        insert(tweets, event);
        removeAgainWhenNoLongerFollowing(event);
    }

    private void insert(List<TweetSummaryClientDTO> tweets, UserFollowedEventDTO event) {
        for (TweetSummaryClientDTO tweet : tweets) {
            // Microseconds are what Postgres stores, and the page cursor is built from this value.
            feedEntryRepository.insertIfAbsent(
                    new UUID[]{event.getFollowerId()},
                    tweet.getId(),
                    event.getFolloweeId(),
                    tweet.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
        }

        log.info("Back-filled {} tweets of user {} into the feed of user {}.", tweets.size(), event.getFolloweeId(), event.getFollowerId());
    }

    private void removeAgainWhenNoLongerFollowing(UserFollowedEventDTO event) {
        if (followLookupService.isFollowing(event.getFollowerId(), event.getFolloweeId())) {
            return;
        }

        int removed = feedEntryRepository
                .deleteByUnfollow(event.getFollowerId(), event.getFolloweeId(), clock.instant().truncatedTo(ChronoUnit.MICROS));

        log.info("Removed {} back-filled entries of user {} by author {}: the follow is already gone.", removed, event.getFollowerId(), event.getFolloweeId());
    }

    private void requireDifferentUsers(UserFollowedEventDTO event) {
        if (!event.getFollowerId().equals(event.getFolloweeId())) {
            return;
        }

        log.warn("Dropping user.followed event {}: user {} cannot follow themselves.", event.getEventId(), event.getFollowerId());

        throw new InvalidEventException("Invalid user.followed event " + event.getEventId() + ": the follower and the followee are the same user.");
    }

    private int requirePositive(int backfillSize) {
        if (backfillSize <= 0) {
            throw new IllegalStateException("app.feed.backfill-size must be positive, but was " + backfillSize + ".");
        }

        return backfillSize;
    }

    private int requireAtMostTweetServiceLimit(int backfillSize) {
        if (backfillSize > MAX_TWEETS_PER_AUTHOR_READ) {
            throw new IllegalStateException("app.feed.backfill-size must not exceed " + MAX_TWEETS_PER_AUTHOR_READ
                    + ", the tweet service's limit, but was " + backfillSize + ".");
        }

        return backfillSize;
    }
}
