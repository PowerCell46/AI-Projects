package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.ViewService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.mappers.TweetItemMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TweetItemAssemblyServiceImpl implements TweetItemAssemblyService {

    private final ViewService viewService;

    private final ExecutorService downstreamCallExecutor;

    private final UserLookupService userLookupService;

    private final TweetLookupService tweetLookupService;

    @Override
    public <T> List<TweetItemResponseDTO> assemble(List<T> rows, Function<T, UUID> tweetIdOf, Function<T, UUID> authorIdOf) {
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
        Map<UUID, Long> viewsByTweetId = viewService.countViews(tweetIds);

        return rows
                .stream()
                .filter(row -> tweetsById.containsKey(tweetIdOf.apply(row)) && authorsById.containsKey(authorIdOf.apply(row)))
                .map(row -> TweetItemMapper.toItem(
                        tweetsById.get(tweetIdOf.apply(row)),
                        authorsById.get(authorIdOf.apply(row)),
                        viewsByTweetId.getOrDefault(tweetIdOf.apply(row), 0L)))
                .toList();
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
