package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedEntryCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedEntryCleanupServiceImpl implements FeedEntryCleanupService {

    private final FeedEntryRepository feedEntryRepository;

    private final TweetViewRepository tweetViewRepository;

    private final SavedTweetRepository savedTweetRepository;

    private final EventValidationService eventValidationService;

    private final TweetViewCountRepository tweetViewCountRepository;

    @Override
    @Transactional
    public void onTweetDeleted(TweetDeletedEventDTO event) {
        eventValidationService.validate(event, "tweet.deleted event " + event.getEventId() + " for tweet " + event.getTweetId());

        int removedFromFeeds = feedEntryRepository.deleteByTweetId(event.getTweetId());
        int removedFromSavedLists = savedTweetRepository.deleteByTweetId(event.getTweetId());
        int removedViews = tweetViewRepository.deleteByTweetId(event.getTweetId());
        tweetViewCountRepository.deleteByTweetId(event.getTweetId());

        log.info(
                "Removed tweet {} from {} feeds and {} saved lists, with its {} views.",
                event.getTweetId(), removedFromFeeds, removedFromSavedLists, removedViews);
    }

    @Override
    public void onUserUnfollowed(UserUnfollowedEventDTO event) {
        eventValidationService.validate(
                event, "user.unfollowed event " + event.getEventId() + " for follower " + event.getFollowerId());

        // Microseconds are what Postgres compares against, so the bound is cut to them rather than rounded up.
        int removed = feedEntryRepository.deleteByUnfollow(
                event.getFollowerId(),
                event.getFolloweeId(),
                event.getOccurredAt().truncatedTo(ChronoUnit.MICROS));

        log.info("Removed {} entries of user {} by author {} after an unfollow.", removed, event.getFollowerId(), event.getFolloweeId());
    }
}
