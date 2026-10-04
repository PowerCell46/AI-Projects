package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailDispatchService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailEventValidationService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.OutgoingMail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.UserConfirmationNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserConfirmationNotificationServiceImpl implements UserConfirmationNotificationService {

    private static final String SUBJECT = "Confirm your email";

    private static final String KEY_PREFIX = "mail:confirmation:";

    private final Clock clock;

    private final Duration sentTtl;

    private final MailDispatchService mailDispatchService;

    private final ConfirmationEmailRenderer confirmationEmailRenderer;

    private final MailEventValidationService mailEventValidationService;

    public UserConfirmationNotificationServiceImpl(
            Clock clock,
            @Value("${app.mail-inbox.confirmation-sent-ttl}") Duration sentTtl,
            MailEventValidationService mailEventValidationService,
            MailDispatchService mailDispatchService,
            ConfirmationEmailRenderer confirmationEmailRenderer
    ) {
        this.clock = clock;
        this.sentTtl = sentTtl;
        this.mailEventValidationService = mailEventValidationService;
        this.mailDispatchService = mailDispatchService;
        this.confirmationEmailRenderer = confirmationEmailRenderer;
    }

    @Override
    public void process(UserConfirmationRequestedEventDTO event) {
        mailEventValidationService.validate(event, "confirmation event with " + ids(event));

        if (hasExpired(event)) {
            log.info("Skipping the confirmation email for {}: the link has already expired.", ids(event));
            return;
        }

        RenderedEmail renderedEmail = confirmationEmailRenderer.render(
                event.getUsername(), event.getConfirmationUrl(), event.getExpiresAt());

        mailDispatchService.dispatchOnce(OutgoingMail.builder()
                .key(KEY_PREFIX + event.getEventId())
                .html(renderedEmail.getHtml())
                .text(renderedEmail.getText())
                .subject(SUBJECT)
                .logLabel("confirmation email for " + ids(event))
                .recipient(event.getEmail())
                .sentTtl(sentTtl)
                .build());
    }

    private boolean hasExpired(UserConfirmationRequestedEventDTO event) {
        return !clock.instant().isBefore(event.getExpiresAt());
    }

    private String ids(UserConfirmationRequestedEventDTO event) {
        return "eventId '" + event.getEventId() + "', userId '" + event.getUserId() + "'";
    }
}
