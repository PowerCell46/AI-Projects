package com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.MailDeliveryService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.OutgoingMail;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.ClaimResult;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.MailInboxService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailDispatchServiceImplTest {

    @Mock
    private MailInboxService mailInboxService;

    @Captor
    private ArgumentCaptor<String> tokenCaptor;

    @Mock
    private MailDeliveryService mailDeliveryService;

    @InjectMocks
    private MailDispatchServiceImpl mailDispatchService;

    private static OutgoingMail outgoingMail() {
        return OutgoingMail
                .builder()
                .key("welcome:123")
                .recipient("ana@example.com")
                .subject("Welcome")
                .html("<p>Welcome</p>")
                .text("Welcome")
                .logLabel("welcome email")
                .sentTtl(Duration.ofDays(7))
                .build();
    }

    @Nested
    class DispatchOnce {

        @Test
        void should_send_and_mark_sent_when_the_claim_is_acquired() {
            OutgoingMail mail = outgoingMail();
            when(mailInboxService.claim(eq(mail.getKey()), anyString())).thenReturn(ClaimResult.CLAIMED);

            mailDispatchService.dispatchOnce(mail);

            verify(mailDeliveryService).send(mail.getRecipient(), mail.getSubject(), mail.getHtml(), mail.getText());
            verify(mailInboxService).markSent(mail.getKey(), mail.getSentTtl());
        }

        @Test
        void should_skip_sending_when_the_mail_was_already_sent() {
            OutgoingMail mail = outgoingMail();
            when(mailInboxService.claim(eq(mail.getKey()), anyString())).thenReturn(ClaimResult.ALREADY_SENT);

            mailDispatchService.dispatchOnce(mail);

            verify(mailDeliveryService, never()).send(anyString(), anyString(), anyString(), anyString());
            verify(mailInboxService, never()).markSent(anyString(), any());
        }

        @Test
        void should_throw_held_when_the_claim_is_held_by_another_token() {
            OutgoingMail mail = outgoingMail();
            when(mailInboxService.claim(eq(mail.getKey()), anyString())).thenReturn(ClaimResult.HELD);

            MailClaimHeldException thrown =
                    assertThrows(MailClaimHeldException.class, () -> mailDispatchService.dispatchOnce(mail));

            assertEquals("The mail inbox claim for key '" + mail.getKey() + "' is held.", thrown.getMessage());
            verify(mailDeliveryService, never()).send(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void should_release_the_claim_with_the_same_token_and_rethrow_when_sending_fails() {
            OutgoingMail mail = outgoingMail();
            RuntimeException failure = new RuntimeException("SMTP down");
            when(mailInboxService.claim(eq(mail.getKey()), anyString())).thenReturn(ClaimResult.CLAIMED);
            doThrow(failure)
                    .when(mailDeliveryService)
                    .send(mail.getRecipient(), mail.getSubject(), mail.getHtml(), mail.getText());

            RuntimeException thrown =
                    assertThrows(RuntimeException.class, () -> mailDispatchService.dispatchOnce(mail));

            assertEquals(failure, thrown);
            verify(mailInboxService).release(eq(mail.getKey()), tokenCaptor.capture());
            verify(mailInboxService).claim(eq(mail.getKey()), eq(tokenCaptor.getValue()));
            verify(mailInboxService, never()).markSent(anyString(), any());
        }
    }
}
