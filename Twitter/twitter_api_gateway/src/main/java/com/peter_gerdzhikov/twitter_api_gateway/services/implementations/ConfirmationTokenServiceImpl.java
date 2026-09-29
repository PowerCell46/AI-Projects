package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationTokenService;

/**
 * Plain SHA-256 is enough to store the token: it already carries 256 bits of entropy, so there is
 * nothing for a slow hash to protect.
 */
@Service
public class ConfirmationTokenServiceImpl implements ConfirmationTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return encode(bytes);
    }

    @Override
    public String hash(String rawToken) {
        return encode(sha256().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform.", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
