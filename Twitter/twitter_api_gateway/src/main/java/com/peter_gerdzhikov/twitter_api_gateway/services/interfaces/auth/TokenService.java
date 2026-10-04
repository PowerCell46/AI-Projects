package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

public interface TokenService {

    String mint(User user);
}
