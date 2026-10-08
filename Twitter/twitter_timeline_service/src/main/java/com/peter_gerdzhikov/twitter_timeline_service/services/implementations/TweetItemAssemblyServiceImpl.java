package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLikeCount;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.savedtweets.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.mappers.TweetItemMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TweetItemAssemblyServiceImpl implements TweetItemAssemblyService {

    private final ViewService viewService;

    private final ExecutorService downstreamCallExecutor;

    private final UserLookupService userLookupService;

    private final TweetLookupService tweetLookupService;

    private final SavedTweetRepository savedTweetRepository;

    private final TweetLikeRepository tweetLikeRepository;

    private final TweetLikeCountRepository tweetLikeCountRepository;

    @Override
    public <T> List<TweetItemResponseDTO> assemble(
            UUID viewerId,
            List<T> rows,
            Function<T, UUID> tweetIdOf,
            Function<T, UUID> authorIdOf
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> tweetIds = new LinkedHashSet<>();
        Set<UUID> authorIds = new LinkedHashSet<>();
        for (T row : rows) {
            tweetIds.add(tweetIdOf.apply(row));
            authorIds.add(authorIdOf.apply(row));
        }

        CompletableFuture<Map<UUID, TweetClientDTO>> tweetsRequest =
                CompletableFuture.supplyAsync(() -> tweetLookupService.findByIds(tweetIds), downstreamCallExecutor);
        CompletableFuture<Map<UUID, UserClientDTO>> authorsRequest =
                CompletableFuture.supplyAsync(() -> userLookupService.findByIds(authorIds), downstreamCallExecutor);
        Map<UUID, TweetClientDTO> tweetsById = await(tweetsRequest);
        Map<UUID, UserClientDTO> authorsById = await(authorsRequest);

        List<TweetClientDTO> tweets = rows
                .stream()
                .filter(row -> authorsById.containsKey(authorIdOf.apply(row)))
                .map(row -> tweetsById.get(tweetIdOf.apply(row)))
                .filter(Objects::nonNull)
                .toList();

        return assembleFetched(viewerId, tweets, authorsById);
    }

    @Override
    public List<TweetItemResponseDTO> assembleFetched(
            UUID viewerId,
            List<TweetClientDTO> tweets,
            Map<UUID, UserClientDTO> authorsById
    ) {
        if (tweets.isEmpty()) {
            return List.of();
        }

        Set<UUID> tweetIds = tweets
                .stream()
                .map(TweetClientDTO::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Long> viewsByTweetId = viewService.countViews(tweetIds);
        Set<UUID> savedTweetIds = Set.copyOf(savedTweetRepository.findSavedTweetIds(viewerId, tweetIds));
        Map<UUID, Long> likesByTweetId = countLikes(tweetIds);
        Set<UUID> likedTweetIds = Set.copyOf(tweetLikeRepository.findLikedTweetIds(viewerId, tweetIds));

        return tweets
                .stream()
                .filter(tweet -> authorsById.containsKey(tweet.getAuthorId()))
                .map(tweet -> TweetItemMapper.toItem(
                        tweet,
                        authorsById.get(tweet.getAuthorId()),
                        viewsByTweetId.getOrDefault(tweet.getId(), 0L),
                        savedTweetIds.contains(tweet.getId()),
                        likesByTweetId.getOrDefault(tweet.getId(), 0L),
                        likedTweetIds.contains(tweet.getId())))
                .toList();
    }

    private Map<UUID, Long> countLikes(Set<UUID> tweetIds) {
        return tweetLikeCountRepository
                .findAllById(tweetIds)
                .stream()
                .collect(Collectors.toMap(TweetLikeCount::getTweetId, TweetLikeCount::getLikes));
    }

    /**
     * Gives back the failure the lookup itself threw, so the caller sees the same exception as from a direct
     * call, not the future's wrapper around it.
     */
    private <T> T await(CompletableFuture<T> request) {
        try {
            return request.join();

        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }

            throw e;
        }
    }
}
