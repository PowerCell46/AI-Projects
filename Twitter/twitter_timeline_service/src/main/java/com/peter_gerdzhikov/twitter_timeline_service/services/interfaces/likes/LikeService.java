package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.likes;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.likes.LikedTweetsResponseDTO;

public interface LikeService {

    /**
     * Likes the tweet for the user. Idempotent: liking again keeps the original like time. The tweet service is
     * asked whether the tweet exists first, and nothing is stored when it doesn't.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException
     *         when the tweet doesn't exist
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the tweet service can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the tweet service doesn't answer within the read timeout
     */
    void like(UUID userId, UUID tweetId);

    /**
     * Removes the user's like of the tweet. Idempotent, and no existence check: a deleted tweet must still be
     * unlikable.
     */
    void unlike(UUID userId, UUID tweetId);

    /**
     * One page of the user's liked tweets, most recently liked first. {@code cursor} is {@code null} for the
     * first page. The tweets and their authors are fetched side by side, one call each, for the ids of this
     * page only. A like whose tweet or author is missing is skipped, so the page can be short; its cursor still
     * moves past every row read, and only a {@code null} {@code nextCursor} ends the list.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException
     *         when {@code size} is outside 1 to 100
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException
     *         when the cursor was not produced by this service
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when either downstream can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when either downstream doesn't answer within the read timeout
     */
    LikedTweetsResponseDTO getLikedTweets(UUID userId, String cursor, int size);
}
