package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import java.util.UUID;

public interface UserService {

    void deleteByEmail(String email, UUID adminId);
}
