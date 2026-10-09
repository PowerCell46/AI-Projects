package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.authortweets;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.authortweets.AuthorTweetsResponseDTO;

public interface AuthorTweetsService {

    /**
     * One page of the author's tweets, newest first, each exactly as a feed item shows it. The author is looked
     * up first, so an unknown one answers before any tweet is read; the cursor is the tweet service's and is
     * passed through.
     *
     * @param cursor the {@code nextCursor} of the previous page, or {@code null} for the first page
     * @param size   between 1 and 100
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidPageSizeException
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidCursorException
     *         when the tweet service refuses the cursor
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException
     *         when the gateway doesn't know the author, or the account isn't confirmed
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when either downstream can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when either downstream doesn't answer within the read timeout
     */
    AuthorTweetsResponseDTO list(UUID viewerId, UUID authorId, String cursor, int size);
}
