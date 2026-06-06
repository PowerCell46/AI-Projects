package com.spotystats.backend.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Application-level settings bound from the {@code app.*} configuration tree.
 *
 * @param frontendBaseUri browser-facing origin of the SPA, used for post-login and error redirects
 * @param tokenEncryption symmetric-encryption material protecting refresh tokens at rest
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        String frontendBaseUri,

        TokenEncryption tokenEncryption
) {

    /**
     * @param password passphrase fed into the key-derivation function
     * @param salt     hex-encoded salt for the key-derivation function
     */
    public record TokenEncryption(

            String password,

            String salt
    ) {
    }
}
