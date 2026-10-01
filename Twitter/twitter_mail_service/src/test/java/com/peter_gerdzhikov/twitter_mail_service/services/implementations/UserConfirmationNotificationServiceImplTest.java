package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.TransientMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailDeliveryService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailInboxService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class UserConfirmationNotificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private static final Duration SENT_TTL = Duration.ofDays(7);

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private MailInboxService mailInboxService;

    @Mock
    private MailDeliveryService mailDeliveryService;

    private UserConfirmationNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserConfirmationNotificationServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC),
                SENT_TTL,
                new MailEventValidationServiceImpl(VALIDATOR),
                new MailDispatchServiceImpl(mailInboxService, mailDeliveryService),
                new ConfirmationEmailRenderer("Europe/Sofia"));
    }

    @Nested
    class Process {

        @Test
        void should_claim_then_send_then_mark_sent_in_that_order() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            String key = "mail:confirmation:" + event.getEventId();
            when(mailInboxService.claim(eq(key), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            InOrder inOrder = inOrder(mailInboxService, mailDeliveryService);
            inOrder.verify(mailInboxService).claim(eq(key), anyString());
            inOrder.verify(mailDeliveryService).send(
                    eq(event.getEmail()),
                    eq("Confirm your email"),
                    argThat(html -> html.contains(event.getConfirmationUrl())),
                    argThat(text -> text.contains(event.getConfirmationUrl())));
            inOrder.verify(mailInboxService).markSent(key, SENT_TTL);
        }

        @Test
        void should_release_the_claim_with_the_same_token_it_claimed_with_when_the_send_fails() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            String key = "mail:confirmation:" + event.getEventId();
            when(mailInboxService.claim(eq(key), anyString())).thenReturn(ClaimResult.CLAIMED);
            TransientMailDeliveryException failure = new TransientMailDeliveryException("smtp down");
            doThrow(failure).when(mailDeliveryService).send(anyString(), anyString(), anyString(), anyString());

            TransientMailDeliveryException thrown = assertThrows(TransientMailDeliveryException.class,
                    () -> service.process(event));

            assertSame(failure, thrown);
            ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
            verify(mailInboxService).claim(eq(key), claimToken.capture());
            verify(mailInboxService).release(key, claimToken.getValue());
            verify(mailInboxService, never()).markSent(anyString(), eq(SENT_TTL));
        }

        @Test
        void should_release_the_claim_and_rethrow_when_the_send_fails_permanently() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);
            doThrow(new PermanentMailDeliveryException("rejected"))
                    .when(mailDeliveryService)
                    .send(anyString(), anyString(), anyString(), anyString());

            assertThrows(PermanentMailDeliveryException.class, () -> service.process(event));

            verify(mailInboxService).release(anyString(), anyString());
        }

        @Test
        void should_swallow_a_mark_sent_failure_without_releasing_the_claim() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);
            doThrow(new IllegalStateException("redis down")).when(mailInboxService).markSent(anyString(), eq(SENT_TTL));

            assertDoesNotThrow(() -> service.process(event));

            verify(mailInboxService, never()).release(anyString(), anyString());
        }

        @Test
        void should_skip_sending_when_the_key_is_already_sent() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.ALREADY_SENT);

            service.process(event);

            verifyNoInteractions(mailDeliveryService);
            verify(mailInboxService, never()).markSent(anyString(), eq(SENT_TTL));
        }

        @Test
        void should_throw_a_retryable_exception_and_send_nothing_when_the_claim_is_held() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plus(Duration.ofHours(1)));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.HELD);

            assertThrows(MailClaimHeldException.class, () -> service.process(event));

            verifyNoInteractions(mailDeliveryService);
            verify(mailInboxService, never()).release(anyString(), anyString());
        }
    }

    @Nested
    class Staleness {

        @Test
        void should_skip_without_touching_the_inbox_when_expires_at_equals_the_clock() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW);

            service.process(event);

            verifyNoInteractions(mailInboxService, mailDeliveryService);
        }

        @Test
        void should_skip_without_touching_the_inbox_when_expires_at_is_before_the_clock() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.minusSeconds(1));

            service.process(event);

            verifyNoInteractions(mailInboxService, mailDeliveryService);
        }

        @Test
        void should_send_when_expires_at_is_one_second_after_the_clock() {
            UserConfirmationRequestedEventDTO event = anEvent(NOW.plusSeconds(1));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            verify(mailDeliveryService).send(eq(event.getEmail()), anyString(), anyString(), anyString());
        }
    }

    @Nested
    class Validate {

        @Test
        void should_throw_and_touch_nothing_when_the_event_is_invalid() {
            UserConfirmationRequestedEventDTO invalidEvent = anEvent(NOW.plus(Duration.ofHours(1)));
            invalidEvent.setEventId(null);

            assertThrows(InvalidMailEventException.class, () -> service.process(invalidEvent));

            verifyNoInteractions(mailInboxService, mailDeliveryService);
        }

        @Test
        void should_log_the_ids_and_the_invalid_field_but_never_the_address_or_the_link(CapturedOutput output) {
            UserConfirmationRequestedEventDTO invalidEvent = anEvent(NOW.plus(Duration.ofHours(1)));
            invalidEvent.setUsername("a-b");
            invalidEvent.setEmail("victim.secret@example.com");
            invalidEvent.setConfirmationUrl("http://localhost:5173/confirm?token=SECRETTOKEN");

            assertThrows(InvalidMailEventException.class, () -> service.process(invalidEvent));

            assertTrue(output.getOut().contains(invalidEvent.getEventId().toString()));
            assertTrue(output.getOut().contains("username"));
            assertFalse(output.getOut().contains("victim.secret@example.com"));
            assertFalse(output.getOut().contains("SECRETTOKEN"));
        }
    }

    private static UserConfirmationRequestedEventDTO anEvent(Instant expiresAt) {
        return UserConfirmationRequestedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .email("recipient@example.com")
                .username("ana_k")
                .confirmationUrl("http://localhost:5173/confirm?token=abc")
                .expiresAt(expiresAt)
                .build();
    }
}
