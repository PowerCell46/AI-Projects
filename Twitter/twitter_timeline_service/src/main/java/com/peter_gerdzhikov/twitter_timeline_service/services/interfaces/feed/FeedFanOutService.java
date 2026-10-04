package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;

public interface FeedFanOutService {

    /**
     * Puts the new tweet in the author's own feed and in the feed of every follower the gateway reports at this
     * moment, one follower page at a time, each page in its own transaction. Safe to run again after a failure:
     * entries that already exist are skipped, so nobody gets the tweet twice and the rerun finishes what the
     * first run didn't.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException
     *         when the event fails validation - not retryable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the gateway can't be reached or answers an error - retryable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the gateway doesn't answer in time - retryable
     */
    void fanOut(TweetCreatedEventDTO event);
}
