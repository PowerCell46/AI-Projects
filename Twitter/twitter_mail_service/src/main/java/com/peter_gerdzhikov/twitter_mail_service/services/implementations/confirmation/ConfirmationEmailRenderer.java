package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationEmailRenderer {

    private static final String USERNAME_TOKEN = "USERNAME";

    private static final String EXPIRES_AT_TOKEN = "EXPIRES_AT";

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm zzz", Locale.ENGLISH);

    private static final String CONFIRMATION_URL_TOKEN = "CONFIRMATION_URL";

    private static final String TEXT_TEMPLATE_PATH = "classpath:templates/confirmationEmail.txt";

    private static final String HTML_TEMPLATE_PATH = "classpath:templates/confirmationEmail.html";

    private final ZoneId zone;

    private final EmailTemplate emailTemplate;

    public ConfirmationEmailRenderer(@Value("${app.mail.zone}") String zone) {
        this.zone = ZoneId.of(zone);
        this.emailTemplate = new EmailTemplate(
                HTML_TEMPLATE_PATH,
                TEXT_TEMPLATE_PATH,
                Set.of(USERNAME_TOKEN, EXPIRES_AT_TOKEN, CONFIRMATION_URL_TOKEN));
    }

    public RenderedEmail render(String username, String confirmationUrl, Instant expiresAt) {
        return emailTemplate.render(Map.of(
                USERNAME_TOKEN, username,
                CONFIRMATION_URL_TOKEN, confirmationUrl,
                EXPIRES_AT_TOKEN, expiresAt.atZone(zone).format(EXPIRES_AT_FORMATTER)));
    }
}
