package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.internal;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.FollowerIdsResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;

public interface InternalUserService {

    /**
     * The ids of the users who follow {@code userId}, newest follow first; {@code cursor} is {@code null} for
     * the first page. An unknown user has no followers, so it answers an empty page rather than an error.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException
     *         when the cursor was not produced by this service
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException
     *         when {@code size} is outside 1 to 1,000
     */
    FollowerIdsResponseDTO getFollowerIds(UUID userId, String cursor, int size);

    /**
     * The confirmed users among the ids, in no particular order; unknown and unconfirmed ids are left out and
     * repeated ids collapse.
     *
     * @param ids between 1 and 100 ids, repeats included
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserIdsOutOfRangeException
     */
    List<InternalUserResponseDTO> getUsers(List<UUID> ids);
}
