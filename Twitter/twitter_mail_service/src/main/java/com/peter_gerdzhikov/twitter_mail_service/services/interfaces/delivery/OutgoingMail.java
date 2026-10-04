package com.peter_gerdzhikov.twitter_mail_service.services.interfaces;

import java.time.Duration;

import lombok.Builder;
import lombok.Value;

/**
 * Everything {@link MailDispatchService} needs to send one deduped email. {@code logLabel} names the email in
 * log lines, so it may carry ids but never the address or a token.
 */
@Value
@Builder
public class OutgoingMail {

    private final String key;

    private final String html;

    private final String text;

    private final String subject;

    private final String logLabel;

    private final String recipient;

    private final Duration sentTtl;
}
