package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;

public interface AuthService {

    User register(RegisterRequestDTO request);

    User login(LoginRequestDTO request);

    void ensureAdminExists(String email, String password);
}
