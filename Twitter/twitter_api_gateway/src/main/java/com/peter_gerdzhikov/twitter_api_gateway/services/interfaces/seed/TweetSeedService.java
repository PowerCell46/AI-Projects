package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;

public interface TweetSeedService {

    /**
     * Returns the tweets that were created; fewer than asked for, or none, when the tweet service fails.
     */
    List<SeededTweetClientDTO> seedTweets(List<UUID> authorIds);
}
