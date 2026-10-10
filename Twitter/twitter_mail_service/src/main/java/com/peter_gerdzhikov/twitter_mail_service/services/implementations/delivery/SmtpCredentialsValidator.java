package com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SmtpCredentialsValidator {

    public SmtpCredentialsValidator(
            @Value("${spring.mail.properties.mail.smtp.auth}") boolean isAuthEnabled,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password
    ) {
        if (isAuthEnabled && !(StringUtils.hasText(username) && StringUtils.hasText(password))) {
            throw new IllegalStateException(
                    "MAIL_USERNAME and MAIL_PASSWORD must both be set while MAIL_SMTP_AUTH is true.");
        }
    }
}
