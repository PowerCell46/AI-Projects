package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.tweetdetails;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

public interface TweetDetailsService {

    /**
     * The tweet exactly as a feed item shows it. The tweet is read first, then its author, one call each, because
     * the author id is known only once the tweet is read; no call for the author is made when the tweet is gone.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException
     *         when the tweet service no longer knows the tweet
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException
     *         when the gateway no longer knows its author
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when either downstream can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when either downstream doesn't answer within the read timeout
     */
    TweetItemResponseDTO get(UUID viewerId, UUID tweetId);
}
