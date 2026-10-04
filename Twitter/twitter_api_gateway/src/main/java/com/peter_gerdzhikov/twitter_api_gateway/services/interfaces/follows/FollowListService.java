package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListResponseDTO;

public interface FollowListService {

    /**
     * Newest follow first. {@code cursor} is {@code null} for the first page.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException
     *         when the cursor was not produced by this service
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException
     *         when {@code size} is outside 1 to 100
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException
     *         for an unknown or unconfirmed user
     */
    FollowListResponseDTO getFollowers(UUID viewerId, String username, String cursor, int size);

    /**
     * Same rules as {@link #getFollowers}.
     */
    FollowListResponseDTO getFollowing(UUID viewerId, String username, String cursor, int size);
}
