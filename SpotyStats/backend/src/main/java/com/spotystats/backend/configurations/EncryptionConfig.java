package com.spotystats.backend.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;


@Configuration
public class EncryptionConfig {

    /**
     * AES-256-GCM text encryptor used to protect refresh tokens at rest. The password
     * and (hex-encoded) salt come from configuration and must be overridden per environment.
     */
    @Bean
    public TextEncryptor refreshTokenEncryptor(AppProperties appProperties) {
        final AppProperties.TokenEncryption encryption = appProperties.tokenEncryption();

        return Encryptors.delux(encryption.password(), encryption.salt());
    }
}
