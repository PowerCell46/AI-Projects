package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views;

import java.util.Collection;
import java.util.UUID;

public interface ViewRecordingService {

    /**
     * Records the viewer for each tweet and bumps the counter of each tweet that was new to them, in one
     * transaction, so a counter can never disagree with its view rows.
     *
     * @param tweetIds distinct ids of tweets that exist
     * @return the number of tweets that were new to the viewer
     */
    int record(UUID viewerId, Collection<UUID> tweetIds);
}
