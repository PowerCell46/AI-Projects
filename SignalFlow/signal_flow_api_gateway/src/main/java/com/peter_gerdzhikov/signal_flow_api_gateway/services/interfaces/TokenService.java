package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;

public interface TokenService {

    String mint(User user);
}
