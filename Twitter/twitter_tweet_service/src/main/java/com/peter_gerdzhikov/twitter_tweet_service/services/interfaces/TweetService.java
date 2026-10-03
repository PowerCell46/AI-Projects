package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetImageContentResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;

public interface TweetService {

    /**
     * Validates everything before storing anything, then stores the images, then saves the tweet and its
     * {@code tweet.created} outbox message in one transaction. The images are deleted again if either later
     * step fails.
     *
     * @param content the raw text, or {@code null} when the part was absent
     * @param images  the uploaded files, or {@code null} when there were none
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.EmptyTweetException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TooManyImagesException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.EmptyUploadException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.UnsupportedImageTypeException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.StorageUnavailableException
     */
    TweetResponseDTO create(UUID authorId, String content, List<MultipartFile> images);

    /**
     * Counts the read as a view, atomically, and returns the tweet with the count including this view.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException
     */
    TweetResponseDTO get(UUID tweetId);

    /**
     * Reads the tweets that exist among the ids, in no particular order; unknown ids are left out and repeated
     * ids collapse. Doesn't count a view.
     *
     * @param ids between 1 and 100 ids, repeats included
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetIdsOutOfRangeException
     */
    List<TweetResponseDTO> findByIds(List<UUID> ids);

    /**
     * Doesn't count a view.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetImageNotFoundException
     */
    TweetImageContentResponseDTO openImage(UUID tweetId, UUID imageId);

    /**
     * Changes the text only, and only for the author. The content rules apply against the stored tweet's
     * images. Writes no event.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.NotTweetAuthorException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.EmptyTweetException
     */
    TweetResponseDTO update(UUID callerId, UUID tweetId, String content);

    /**
     * Hard delete, for the author only. The tweet and its {@code tweet.deleted} outbox message go in one
     * transaction; the image objects are deleted after it commits, and a failure there is only logged.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.NotTweetAuthorException
     */
    void delete(UUID callerId, UUID tweetId);
}
