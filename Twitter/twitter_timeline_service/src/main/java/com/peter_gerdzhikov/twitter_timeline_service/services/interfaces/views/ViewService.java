package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ViewService {

    /**
     * Counts the viewer once for each reported tweet that exists and that they have not viewed before. Ids the
     * tweet service doesn't know are dropped silently; repeated ids count once.
     *
     * @param tweetIds 1 to 50 ids, counted as sent
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidTweetIdsException
     *         when the list is missing, empty, too long or holds a {@code null}
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the tweet service can't be reached; nothing is recorded
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the tweet service doesn't answer in time; nothing is recorded
     */
    void report(UUID viewerId, List<UUID> tweetIds);

    /**
     * The number of unique viewers of each requested tweet: one entry per distinct id, in request order, and
     * {@code 0} for a tweet nobody viewed or that doesn't exist. Calls no other service, so a read creates
     * nothing.
     *
     * @param tweetIds 1 to 100 ids, counted as sent
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidTweetIdsException
     *         when the list is missing, empty, too long or holds a {@code null}
     */
    Map<UUID, Long> getViews(List<UUID> tweetIds);

    /**
     * The same counts as {@link #getViews}, without the list-size check, for a caller that already holds a
     * bounded set of ids, such as a page of a feed. One query for all the ids.
     */
    Map<UUID, Long> countViews(Collection<UUID> tweetIds);
}
