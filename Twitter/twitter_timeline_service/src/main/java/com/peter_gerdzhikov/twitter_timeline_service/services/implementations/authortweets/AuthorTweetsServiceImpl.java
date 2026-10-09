package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.authortweets;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetPageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.authortweets.AuthorTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.authortweets.AuthorTweetsService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.PageSizeValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthorTweetsServiceImpl implements AuthorTweetsService {

    private final UserLookupService userLookupService;

    private final TweetLookupService tweetLookupService;

    private final TweetItemAssemblyService tweetItemAssemblyService;

    @Override
    public AuthorTweetsResponseDTO list(UUID viewerId, UUID authorId, String cursor, int size) {
        PageSizeValidator.validate(size);
        UserClientDTO author = userLookupService
                .findByIds(List.of(authorId))
                .get(authorId);

        if (author == null) {
            throw new AuthorNotFoundException();
        }

        TweetPageClientDTO page = tweetLookupService.findPageByAuthor(authorId, cursor, size);

        return AuthorTweetsResponseDTO
                .builder()
                .nextCursor(page.getNextCursor())
                .items(tweetItemAssemblyService.assembleFetched(viewerId, page.getItems(), Map.of(authorId, author)))
                .build();
    }
}
