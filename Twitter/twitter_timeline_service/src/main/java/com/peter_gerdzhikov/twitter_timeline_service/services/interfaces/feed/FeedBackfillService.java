package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserFollowedEventDTO;

public interface FeedBackfillService {

    /**
     * Puts the followee's newest tweets of the retention window before the follow into the follower's feed, then
     * asks the gateway whether the follow still exists and, if it doesn't, removes the followee's entries again.
     * Inserting before checking leaves no window in which a back-fill outlives an unfollow: an unfollow that
     * commits after the check emits an event whose delete runs after the rows exist. Safe to run again after a
     * failure, because every step is idempotent.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException
     *         when the event fails validation - not retryable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the tweet service or the gateway can't be reached or answers an error - retryable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the tweet service or the gateway doesn't answer in time - retryable
     */
    void backfill(UserFollowedEventDTO event);
}
