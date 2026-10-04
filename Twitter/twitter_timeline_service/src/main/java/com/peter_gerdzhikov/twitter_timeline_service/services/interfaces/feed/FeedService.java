package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.FeedResponseDTO;

public interface FeedService {

    /**
     * One page of the user's feed, newest tweet first. {@code cursor} is {@code null} for the first page. The
     * tweets and their authors are fetched side by side, one call each, for the ids of this page only. An
     * entry whose tweet or author is missing is skipped, so the page can be short; its cursor still moves past
     * every entry read, and only a {@code null} {@code nextCursor} ends the feed. Reading writes nothing.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException
     *         when {@code size} is outside 1 to 100
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException
     *         when the cursor was not produced by this service
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when either downstream can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when either downstream doesn't answer within the read timeout
     */
    FeedResponseDTO getFeed(UUID userId, String cursor, int size);
}
