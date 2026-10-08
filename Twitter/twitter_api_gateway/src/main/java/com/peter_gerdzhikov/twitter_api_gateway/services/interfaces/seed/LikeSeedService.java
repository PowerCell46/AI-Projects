package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.client.SeededTweetClientDTO;

public interface LikeSeedService {

    /**
     * Each user likes a random share of the tweets written by other users.
     */
    void seedLikes(List<UUID> userIds, List<SeededTweetClientDTO> tweets);
}
