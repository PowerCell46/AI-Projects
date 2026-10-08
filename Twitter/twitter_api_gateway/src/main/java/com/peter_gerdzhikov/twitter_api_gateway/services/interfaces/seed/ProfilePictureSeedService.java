package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed;

import java.util.List;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

public interface ProfilePictureSeedService {

    /**
     * Gives each user the image in the seed pictures directory that is named after their username.
     * Users without an image keep the default avatar.
     */
    void seedProfilePictures(List<User> users);
}
