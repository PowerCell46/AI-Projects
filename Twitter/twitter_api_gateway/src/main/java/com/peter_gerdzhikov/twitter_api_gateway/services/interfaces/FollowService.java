package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces;

import java.util.UUID;

public interface FollowService {

    /**
     * Idempotent: following someone already followed changes nothing. The username's case is ignored.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.SelfFollowException
     *         when the target is the follower
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException
     *         for an unknown or unconfirmed target
     */
    void follow(UUID followerId, String targetUsername);

    /**
     * Idempotent: unfollowing someone not followed changes nothing. Same failures as {@link #follow}.
     */
    void unfollow(UUID followerId, String targetUsername);
}
