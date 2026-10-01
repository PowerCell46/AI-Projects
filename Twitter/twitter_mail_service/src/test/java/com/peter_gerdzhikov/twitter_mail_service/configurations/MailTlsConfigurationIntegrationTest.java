package com.peter_gerdzhikov.twitter_mail_service.configurations;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.peter_gerdzhikov.twitter_mail_service.support.AbstractRedisIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Its own context with STARTTLS turned on (the shared e2e context turns it off to reach Mailpit), proving the
 * default SMTP setup refuses a plaintext fallback and checks the server's certificate against its host name.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.mail.properties.mail.smtp.starttls.enable=true")
class MailTlsConfigurationIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private JavaMailSenderImpl javaMailSender;

    @Test
    void should_require_starttls_and_check_the_server_identity_when_starttls_is_enabled() {
        Properties mailProperties = javaMailSender.getJavaMailProperties();

        assertEquals("true", mailProperties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", mailProperties.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", mailProperties.getProperty("mail.smtp.ssl.checkserveridentity"));
    }
}
