package com.peter_gerdzhikov.twitter_api_gateway.repositories.projections;

import java.time.Instant;
import java.util.UUID;

/**
 * Just what a page of follower ids and its cursor need, so a page never loads the follower rows.
 */
public interface FollowerEdge {

    UUID getFollowId();

    UUID getFollowerId();

    Instant getCreatedAt();
}
