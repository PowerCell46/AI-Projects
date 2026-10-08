package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;

public interface ReplySeedService {

    /**
     * Gives each tweet a random number of replies, none of them written by the tweet's own author.
     */
    void seedReplies(List<UUID> userIds, List<SeededTweetClientDTO> tweets);
}
