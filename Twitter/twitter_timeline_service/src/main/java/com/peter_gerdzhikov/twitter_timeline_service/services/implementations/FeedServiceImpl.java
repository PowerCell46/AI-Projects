package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.FeedResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.mappers.TweetItemMapper;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.PageSizeValidator;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursor;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final ExecutorService downstreamCallExecutor;

    private final UserLookupService userLookupService;

    private final TweetLookupService tweetLookupService;

    private final FeedEntryRepository feedEntryRepository;

    @Override
    public FeedResponseDTO getFeed(UUID userId, String cursor, int size) {
        PageSizeValidator.validate(size);
        TimelineCursor decodedCursor = cursor == null ? null : TimelineCursorCodec.decode(cursor);

        // One row beyond the page tells whether a next page exists.
        List<FeedEntry> rows = fetchEntries(userId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<FeedEntry> pageRows = hasNext ? rows.subList(0, size) : rows;

        return FeedResponseDTO
                .builder()
                .items(toItems(pageRows))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private List<FeedEntry> fetchEntries(UUID userId, TimelineCursor cursor, Pageable limit) {
        if (cursor == null) {
            return feedEntryRepository.findFirstPage(userId, limit);
        }

        return feedEntryRepository.findPageAfter(userId, cursor.getTimestamp(), cursor.getTweetId(), limit);
    }

    private List<TweetItemResponseDTO> toItems(List<FeedEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        Set<UUID> tweetIds = new LinkedHashSet<>();
        Set<UUID> authorIds = new LinkedHashSet<>();
        for (FeedEntry entry : entries) {
            tweetIds.add(entry.getTweetId());
            authorIds.add(entry.getAuthorId());
        }

        CompletableFuture<Map<UUID, TweetClientDTO>> tweetsRequest =
                CompletableFuture.supplyAsync(() -> tweetLookupService.findByIds(tweetIds), downstreamCallExecutor);
        CompletableFuture<Map<UUID, UserClientDTO>> authorsRequest =
                CompletableFuture.supplyAsync(() -> userLookupService.findByIds(authorIds), downstreamCallExecutor);
        Map<UUID, TweetClientDTO> tweetsById = await(tweetsRequest);
        Map<UUID, UserClientDTO> authorsById = await(authorsRequest);

        return entries
                .stream()
                .filter(entry -> tweetsById.containsKey(entry.getTweetId()) && authorsById.containsKey(entry.getAuthorId()))
                .map(entry -> TweetItemMapper.toItem(tweetsById.get(entry.getTweetId()), authorsById.get(entry.getAuthorId())))
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

    private String cursorAfter(FeedEntry lastRow) {
        return TimelineCursorCodec.encode(lastRow.getTweetCreatedAt(), lastRow.getTweetId());
    }
}
