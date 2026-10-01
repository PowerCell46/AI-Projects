package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailDeliveryService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailDispatchService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailInboxService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.OutgoingMail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDispatchServiceImpl implements MailDispatchService {

    private final MailInboxService mailInboxService;

    private final MailDeliveryService mailDeliveryService;

    @Override
    public void dispatchOnce(OutgoingMail mail) {
        String token = UUID.randomUUID().toString();

        switch (mailInboxService.claim(mail.getKey(), token)) {
            case ALREADY_SENT -> log.info("The {} was already sent; skipping.", mail.getLogLabel());
            case HELD -> throw new MailClaimHeldException(
                    "The mail inbox claim for key '" + mail.getKey() + "' is held.");
            case CLAIMED -> sendAndMark(mail, token);
        }
    }

    private void sendAndMark(OutgoingMail mail, String token) {
        try {
            mailDeliveryService.send(mail.getRecipient(), mail.getSubject(), mail.getHtml(), mail.getText());

        } catch (RuntimeException e) {
            mailInboxService.release(mail.getKey(), token);
            log.warn("Sending the {} failed: {}.", mail.getLogLabel(), e.getClass().getSimpleName());
            throw e;
        }

        try {
            mailInboxService.markSent(mail.getKey(), mail.getSentTtl());

        } catch (RuntimeException e) {
            log.error("Failed to mark the {} as sent after a successful send.", mail.getLogLabel(), e);
        }
    }
}
