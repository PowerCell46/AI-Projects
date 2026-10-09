package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetImageContentResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetSummaryResponseDTO;

public interface TweetService {

    /**
     * Validates everything before storing anything, then stores the images, then saves the tweet and its
     * {@code tweet.created} outbox message in one transaction. The images are deleted again if either later
     * step fails.
     *
     * @param content the raw text, or {@code null} when the part was absent
     * @param images  the uploaded files, or {@code null} when there were none
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content.TweetContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content.EmptyTweetException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content.TooManyImagesException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.EmptyUploadException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.UnsupportedImageTypeException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.StorageUnavailableException
     */
    TweetResponseDTO create(UUID authorId, String content, List<MultipartFile> images);

    /**
     * Counts the read as a view, atomically, and returns the tweet with the count including this view.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     */
    TweetResponseDTO get(UUID tweetId);

    /**
     * Reads the tweets that exist among the ids, in no particular order; unknown ids are left out and repeated
     * ids collapse. Doesn't count a view.
     *
     * @param ids between 1 and 100 ids, repeats included
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.reads.TweetIdsOutOfRangeException
     */
    List<TweetResponseDTO> findByIds(List<UUID> ids);

    /**
     * Reads the id and creation time of the author's tweets created at or after {@code since}, newest first.
     *
     * @param limit between 1 and 100
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.reads.TweetLimitOutOfRangeException
     */
    List<TweetSummaryResponseDTO> findNewestByAuthor(UUID authorId, Instant since, int limit);

    /**
     * Reads one page of the author's tweets, newest first, with every field of a tweet. Doesn't count a view.
     *
     * @param cursor the {@code nextCursor} of the previous page, or {@code null} for the first page
     * @param size   between 1 and 100
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging.InvalidPageSizeException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging.InvalidCursorException
     */
    TweetPageResponseDTO findPageByAuthor(UUID authorId, String cursor, int size);

    /**
     * Counts the author's tweets; an author with none, or an unknown one, has {@code 0}.
     */
    long countByAuthor(UUID authorId);

    /**
     * Doesn't count a view.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetImageNotFoundException
     */
    TweetImageContentResponseDTO openImage(UUID tweetId, UUID imageId);

    /**
     * Changes the text only, and only for the author. The content rules apply against the stored tweet's
     * images. Writes no event.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.NotTweetAuthorException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content.TweetContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.content.EmptyTweetException
     */
    TweetResponseDTO update(UUID callerId, UUID tweetId, String content);

    /**
     * Hard delete, for the author only. The tweet and its {@code tweet.deleted} outbox message go in one
     * transaction; the image objects are deleted after it commits, and a failure there is only logged.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.NotTweetAuthorException
     */
    void delete(UUID callerId, UUID tweetId);
}
