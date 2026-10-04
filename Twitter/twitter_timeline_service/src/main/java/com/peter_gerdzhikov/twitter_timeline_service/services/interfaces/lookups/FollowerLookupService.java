package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface FollowerLookupService {

    /**
     * Hands the ids of the users who follow {@code userId} to {@code pageConsumer}, 1,000 at a time, newest
     * follow first. Each page is consumed before the next is fetched, so the caller can write page by page and
     * nothing holds all the followers at once. An empty page is not handed over. A failure of the consumer
     * stops the paging and propagates.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the gateway can't be reached, answers an error or sends something unreadable
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the gateway doesn't answer within the read timeout
     */
    void forEachFollowerPage(UUID userId, Consumer<List<UUID>> pageConsumer);
}
