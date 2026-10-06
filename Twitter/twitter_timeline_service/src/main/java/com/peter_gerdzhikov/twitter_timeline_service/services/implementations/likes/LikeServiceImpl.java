package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.likes;

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
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.likes.LikedTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLike;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes.LikeRecordingService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes.LikeService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.PageSizeValidator;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursor;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final Clock clock;

    private final TweetLookupService tweetLookupService;

    private final TweetLikeRepository tweetLikeRepository;

    private final LikeRecordingService likeRecordingService;

    private final TweetItemAssemblyService tweetItemAssemblyService;

    @Override
    public void like(UUID userId, UUID tweetId) {
        Map<UUID, TweetClientDTO> tweets = tweetLookupService.findByIds(Set.of(tweetId));
        TweetClientDTO tweet = tweets.get(tweetId);
        if (tweet == null) {
            throw new TweetNotFoundException();
        }

        Instant likedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        boolean added = likeRecordingService.like(userId, tweetId, tweet.getAuthorId(), likedAt);

        log.info("User {} liked tweet {}, new like: {}.", userId, tweetId, added);
    }

    @Override
    public void unlike(UUID userId, UUID tweetId) {
        boolean removed = likeRecordingService.unlike(userId, tweetId);

        log.info("User {} unliked tweet {}, was liked: {}.", userId, tweetId, removed);
    }

    @Override
    public LikedTweetsResponseDTO getLikedTweets(UUID userId, String cursor, int size) {
        PageSizeValidator.validate(size);
        TimelineCursor decodedCursor = cursor == null ? null : TimelineCursorCodec.decode(cursor);

        // One row beyond the page tells whether a next page exists.
        List<TweetLike> rows = fetchRows(userId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<TweetLike> pageRows = hasNext ? rows.subList(0, size) : rows;

        return LikedTweetsResponseDTO
                .builder()
                .items(tweetItemAssemblyService.assemble(userId, pageRows, TweetLike::getTweetId, TweetLike::getAuthorId))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private List<TweetLike> fetchRows(UUID userId, TimelineCursor cursor, Pageable limit) {
        if (cursor == null) {
            return tweetLikeRepository.findFirstPage(userId, limit);
        }

        return tweetLikeRepository.findPageAfter(userId, cursor.getTimestamp(), cursor.getTweetId(), limit);
    }

    private String cursorAfter(TweetLike lastRow) {
        return TimelineCursorCodec.encode(lastRow.getLikedAt(), lastRow.getTweetId());
    }
}
