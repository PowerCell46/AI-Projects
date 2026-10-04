package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups;

import java.util.UUID;

public interface FollowLookupService {

    /**
     * Whether the follow exists at this moment, by one lookup at the gateway. No follow, unknown ids and a
     * self-follow all answer false.
     *
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException
     *         when the gateway can't be reached or answers anything but "yes" or "no"
     * @throws com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException
     *         when the gateway doesn't answer within the read timeout
     */
    boolean isFollowing(UUID followerId, UUID followeeId);
}
