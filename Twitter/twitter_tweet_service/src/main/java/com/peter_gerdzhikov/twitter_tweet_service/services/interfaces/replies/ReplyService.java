package com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.replies;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;

public interface ReplyService {

    /**
     * Looks the caller up at the gateway first, so a failure there leaves nothing written; then, in one
     * transaction, inserts the reply and adds one to the tweet's {@code replyCount}. A tweet that is not there
     * aborts the transaction, so no reply is saved for it.
     *
     * @param content the raw text, or {@code null} when the body had none
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.EmptyReplyException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.CallerUnknownException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.WriteConflictBudgetExceededException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamTimeoutException
     */
    ReplyResponseDTO create(UUID callerId, UUID tweetId, String content);

    /**
     * One page of the tweet's replies, oldest first. A reply whose author the gateway does not return is left
     * out of the page.
     *
     * @param cursor {@code null} for the first page
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging.InvalidCursorException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.paging.InvalidPageSizeException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamTimeoutException
     */
    ReplyPageResponseDTO list(UUID tweetId, String cursor, int size);

    /**
     * Looks the caller up at the gateway first, then rewrites the text with a conditional update that matches the
     * reply only when it belongs to the tweet and to the caller. The same text still counts as an edit.
     *
     * @param content the raw text, or {@code null} when the body had none
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.EmptyReplyException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyContentTooLongException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.CallerUnknownException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyNotFoundException
     *         when the reply is gone, belongs to another tweet or is someone else's
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamTimeoutException
     */
    ReplyResponseDTO update(UUID callerId, UUID tweetId, UUID replyId, String content);

    /**
     * Removes the reply and takes one off the tweet's {@code replyCount} in one transaction, but only when this
     * call removed the reply, so two racing deletes count once.
     *
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyNotFoundException
     *         when the reply is gone, belongs to another tweet, or the caller wrote neither it nor the tweet
     * @throws com.peter_gerdzhikov.twitter_tweet_service.exceptions.WriteConflictBudgetExceededException
     */
    void delete(UUID callerId, UUID tweetId, UUID replyId);
}
