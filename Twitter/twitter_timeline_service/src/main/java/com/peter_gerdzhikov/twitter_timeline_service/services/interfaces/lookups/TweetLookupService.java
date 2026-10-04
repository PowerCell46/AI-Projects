package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetSummaryClientDTO;

public interface TweetLookupService {

    /**
     * The tweets among the ids, by id; an id that no longer exists is absent. No call is made for no ids. The
     * tweet service counts no view for this read.
     *
     * @param ids at most 100 ids, the tweet service's limit; a page never has more
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the tweet service can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the tweet service doesn't answer within the read timeout
     */
    Map<UUID, TweetClientDTO> findByIds(Collection<UUID> ids);

    /**
     * The author's newest tweets created at or after {@code since}, newest first, at most {@code limit}. Only
     * the id and the creation time of each; the tweet service counts no view for this read.
     *
     * @param limit 1 to 100, the tweet service's limit
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the tweet service can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the tweet service doesn't answer within the read timeout
     */
    List<TweetSummaryClientDTO> findNewestByAuthor(UUID authorId, Instant since, int limit);
}
