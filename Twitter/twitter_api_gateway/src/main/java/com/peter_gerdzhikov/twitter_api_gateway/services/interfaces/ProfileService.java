package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;

public interface ProfileService {

    /**
     * The lookup ignores the case of the username. An unconfirmed user doesn't exist to anyone.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException
     *         for an unknown or unconfirmed user
     */
    ProfileResponseDTO getProfile(String username);

    /**
     * A full replacement: every field is overwritten, and a {@code null} or blank string clears it. The
     * pictures are not touched.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException
     *         when the user no longer exists
     */
    ProfileResponseDTO updateProfile(UUID userId, UpdateProfileRequestDTO request);
}
