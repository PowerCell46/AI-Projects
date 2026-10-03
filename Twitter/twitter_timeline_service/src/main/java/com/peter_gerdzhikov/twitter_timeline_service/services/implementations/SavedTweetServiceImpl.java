package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.SavedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.SavedTweet;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.SavedTweetService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.PageSizeValidator;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursor;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedTweetServiceImpl implements SavedTweetService {

    private final Clock clock;

    private final TweetLookupService tweetLookupService;

    private final SavedTweetRepository savedTweetRepository;

    private final TweetItemAssemblyService tweetItemAssemblyService;

    @Override
    public void save(UUID userId, UUID tweetId) {
        Map<UUID, TweetClientDTO> tweets = tweetLookupService.findByIds(Set.of(tweetId));
        TweetClientDTO tweet = tweets.get(tweetId);
        if (tweet == null) {
            throw new TweetNotFoundException();
        }

        Instant savedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        int added = savedTweetRepository.insertIfAbsent(userId, tweetId, tweet.getAuthorId(), savedAt);

        log.info("User {} saved tweet {}, new save: {}.", userId, tweetId, added == 1);
    }

    @Override
    public void unsave(UUID userId, UUID tweetId) {
        int removed = savedTweetRepository.deleteByUserAndTweet(userId, tweetId);

        log.info("User {} unsaved tweet {}, was saved: {}.", userId, tweetId, removed == 1);
    }

    @Override
    public SavedTweetsResponseDTO getSavedTweets(UUID userId, String cursor, int size) {
        PageSizeValidator.validate(size);
        TimelineCursor decodedCursor = cursor == null ? null : TimelineCursorCodec.decode(cursor);

        // One row beyond the page tells whether a next page exists.
        List<SavedTweet> rows = fetchRows(userId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<SavedTweet> pageRows = hasNext ? rows.subList(0, size) : rows;

        return SavedTweetsResponseDTO
                .builder()
                .items(tweetItemAssemblyService.assemble(pageRows, SavedTweet::getTweetId, SavedTweet::getAuthorId))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private List<SavedTweet> fetchRows(UUID userId, TimelineCursor cursor, Pageable limit) {
        if (cursor == null) {
            return savedTweetRepository.findFirstPage(userId, limit);
        }

        return savedTweetRepository.findPageAfter(userId, cursor.getTimestamp(), cursor.getTweetId(), limit);
    }

    private String cursorAfter(SavedTweet lastRow) {
        return TimelineCursorCodec.encode(lastRow.getSavedAt(), lastRow.getTweetId());
    }
}
