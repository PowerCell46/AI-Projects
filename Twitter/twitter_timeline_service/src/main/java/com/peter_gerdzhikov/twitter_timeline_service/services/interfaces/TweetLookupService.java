package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;

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
}
