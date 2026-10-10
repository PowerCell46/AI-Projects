package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows;

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
     * The same as {@link #follow}, except that no {@code user.followed} event is queued, so nobody is emailed and no
     * feed is back-filled. For the demo data seeder only: its follows exist before any tweet does.
     */
    void followWithoutEvent(UUID followerId, String targetUsername);

    /**
     * Idempotent: unfollowing someone not followed changes nothing. Same failures as {@link #follow}. A
     * {@code user.unfollowed} event is queued only when a follow row was actually removed.
     */
    void unfollow(UUID followerId, String targetUsername);
}
