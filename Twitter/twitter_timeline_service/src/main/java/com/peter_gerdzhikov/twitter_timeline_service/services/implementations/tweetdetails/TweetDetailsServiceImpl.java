package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.tweetdetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.tweetdetails.TweetDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TweetDetailsServiceImpl implements TweetDetailsService {

    private final UserLookupService userLookupService;

    private final TweetLookupService tweetLookupService;

    private final TweetItemAssemblyService tweetItemAssemblyService;

    @Override
    public TweetItemResponseDTO get(UUID viewerId, UUID tweetId) {
        TweetClientDTO tweet = tweetLookupService
                .findByIds(List.of(tweetId))
                .get(tweetId);
        if (tweet == null) {
            throw new TweetNotFoundException();
        }

        UserClientDTO author = userLookupService
                .findByIds(List.of(tweet.getAuthorId()))
                .get(tweet.getAuthorId());
        if (author == null) {
            throw new AuthorNotFoundException();
        }

        return tweetItemAssemblyService
                .assembleFetched(viewerId, List.of(tweet), Map.of(author.getId(), author))
                .getFirst();
    }
}
