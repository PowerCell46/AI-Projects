package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

public interface TweetItemAssemblyService {

    /**
     * One item per row, in row order, for the tweet and the author the row names. The tweets and the authors
     * are fetched side by side, one call each, for the distinct ids of these rows only. A row whose tweet or
     * author is missing is skipped, so the result can be shorter than the rows. Each item carries the number of unique
     * viewers of its tweet, read in one query for the page. No call is made for no rows.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when either downstream can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when either downstream doesn't answer within the read timeout
     */
    <T> List<TweetItemResponseDTO> assemble(List<T> rows, Function<T, UUID> tweetIdOf, Function<T, UUID> authorIdOf);
}
