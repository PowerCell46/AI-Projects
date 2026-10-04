package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.FeedResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.feed.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.PageSizeValidator;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursor;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final FeedEntryRepository feedEntryRepository;

    private final TweetItemAssemblyService tweetItemAssemblyService;

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
                .items(tweetItemAssemblyService.assemble(userId, pageRows, FeedEntry::getTweetId, FeedEntry::getAuthorId))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private List<FeedEntry> fetchEntries(UUID userId, TimelineCursor cursor, Pageable limit) {
        if (cursor == null) {
            return feedEntryRepository.findFirstPage(userId, limit);
        }

        return feedEntryRepository.findPageAfter(userId, cursor.getTimestamp(), cursor.getTweetId(), limit);
    }

    private String cursorAfter(FeedEntry lastRow) {
        return TimelineCursorCodec.encode(lastRow.getTweetCreatedAt(), lastRow.getTweetId());
    }
}
