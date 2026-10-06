package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.savedtweets.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.views.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.views.TweetViewRepository;
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

    private final TweetLikeRepository tweetLikeRepository;

    private final SavedTweetRepository savedTweetRepository;

    private final EventValidationService eventValidationService;

    private final TweetViewCountRepository tweetViewCountRepository;

    private final TweetLikeCountRepository tweetLikeCountRepository;

    @Override
    @Transactional
    public void onTweetDeleted(TweetDeletedEventDTO event) {
        eventValidationService.validate(event, "tweet.deleted event " + event.getEventId() + " for tweet " + event.getTweetId());

        int removedFromFeeds = feedEntryRepository.deleteByTweetId(event.getTweetId());
        int removedFromSavedLists = savedTweetRepository.deleteByTweetId(event.getTweetId());
        int removedViews = tweetViewRepository.deleteByTweetId(event.getTweetId());
        tweetViewCountRepository.deleteByTweetId(event.getTweetId());
        int removedLikes = tweetLikeRepository.deleteByTweetId(event.getTweetId());
        tweetLikeCountRepository.deleteByTweetId(event.getTweetId());

        log.info(
                "Removed tweet {} from {} feeds and {} saved lists, with its {} views and {} likes.",
                event.getTweetId(), removedFromFeeds, removedFromSavedLists, removedViews, removedLikes);
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
