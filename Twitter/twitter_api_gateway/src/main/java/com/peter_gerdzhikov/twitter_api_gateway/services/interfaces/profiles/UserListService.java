package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.profiles;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;

public interface UserListService {

    /**
     * Every confirmed user except the caller, newest account first. {@code cursor} is {@code null} for the
     * first page.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException
     *         when {@code size} is outside 1 to 100
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException
     *         when the cursor was not produced by a list service
     */
    UserListResponseDTO listUsers(UUID callerId, String cursor, int size);
}
