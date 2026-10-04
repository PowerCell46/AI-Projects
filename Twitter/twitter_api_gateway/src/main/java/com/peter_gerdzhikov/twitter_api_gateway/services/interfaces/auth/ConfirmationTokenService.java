package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth;

public interface ConfirmationTokenService {

    String generateRawToken();

    String hash(String rawToken);
}
