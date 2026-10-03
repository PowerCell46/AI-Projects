package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedEntryCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedEntryCleanupServiceImpl implements FeedEntryCleanupService {

    private final FeedEntryRepository feedEntryRepository;

    private final EventValidationService eventValidationService;

    @Override
    public void onTweetDeleted(TweetDeletedEventDTO event) {
        eventValidationService.validate(event, "tweet.deleted event " + event.getEventId() + " for tweet " + event.getTweetId());

        int removed = feedEntryRepository.deleteByTweetId(event.getTweetId());

        log.info("Removed tweet {} from {} feeds.", event.getTweetId(), removed);
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
