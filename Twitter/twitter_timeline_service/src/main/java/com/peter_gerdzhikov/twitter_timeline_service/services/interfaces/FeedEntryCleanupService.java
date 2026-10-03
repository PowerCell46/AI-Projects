package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;

public interface FeedEntryCleanupService {

    /**
     * Removes the tweet from every feed and every saved list, in one transaction. Idempotent. If the delete
     * arrives before the tweet's fan-out, the late fan-out leaves entries for a dead tweet; reads skip them
     * because the tweet is missing, and retention removes them.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException
     *         when the event fails validation - not retryable
     */
    void onTweetDeleted(TweetDeletedEventDTO event);

    /**
     * Removes what the followee posted up to the moment of the unfollow from the follower's feed. Tweets posted
     * after it stay, so a quick unfollow and re-follow can't wipe them even when the event arrives late.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException
     *         when the event fails validation - not retryable
     */
    void onUserUnfollowed(UserUnfollowedEventDTO event);
}
