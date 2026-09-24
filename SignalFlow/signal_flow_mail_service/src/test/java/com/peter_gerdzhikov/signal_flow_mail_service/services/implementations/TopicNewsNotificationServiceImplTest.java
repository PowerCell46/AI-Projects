package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.NotificationClaimHeldException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.NotificationInboxService;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.TopicNewsMailService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicNewsNotificationServiceImplTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private TopicNewsMailService topicNewsMailService;

    @Mock
    private NotificationInboxService notificationInboxService;

    private TopicNewsNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopicNewsNotificationServiceImpl(VALIDATOR, topicNewsMailService, notificationInboxService);
    }

    @Nested
    class Validate {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.peter_gerdzhikov.signal_flow_mail_service.services.implementations.TopicNewsNotificationServiceImplTest#invalidEvents")
        void should_throw_and_touch_nothing_when_the_event_is_invalid(String description, TopicNewsNotificationEventDTO invalidEvent) {
            assertThrows(InvalidNotificationEventException.class, () -> service.process(invalidEvent));

            verifyNoInteractions(notificationInboxService, topicNewsMailService);
        }
    }

    @Nested
    class Process {

        @Test
        void should_send_and_mark_sent_when_claimed() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            verify(topicNewsMailService).send(event);
            verify(notificationInboxService).markSent(event.getNewsId(), event.getUserId());
            verify(notificationInboxService, never()).release(any(), any(), any());
        }

        @Test
        void should_not_send_when_already_sent() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.ALREADY_SENT);

            service.process(event);

            verify(topicNewsMailService, never()).send(any());
            verify(notificationInboxService, never()).markSent(any(), any());
        }

        @Test
        void should_throw_notification_claim_held_exception_and_not_send_when_held() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.HELD);

            assertThrows(NotificationClaimHeldException.class, () -> service.process(event));

            verify(topicNewsMailService, never()).send(any());
        }

        @Test
        void should_release_and_rethrow_on_transient_send_failure() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.CLAIMED);
            doThrow(new TransientMailDeliveryException("boom", new RuntimeException())).when(topicNewsMailService).send(event);

            assertThrows(TransientMailDeliveryException.class, () -> service.process(event));

            verify(notificationInboxService).release(eq(event.getNewsId()), eq(event.getUserId()), anyString());
            verify(notificationInboxService, never()).markSent(any(), any());
        }

        @Test
        void should_release_and_rethrow_on_permanent_send_failure() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.CLAIMED);
            doThrow(new PermanentMailDeliveryException("boom", new RuntimeException())).when(topicNewsMailService).send(event);

            assertThrows(PermanentMailDeliveryException.class, () -> service.process(event));

            verify(notificationInboxService).release(eq(event.getNewsId()), eq(event.getUserId()), anyString());
            verify(notificationInboxService, never()).markSent(any(), any());
        }

        @Test
        void should_not_rethrow_or_release_when_mark_sent_throws() {
            TopicNewsNotificationEventDTO event = validEvent();
            when(notificationInboxService.claim(eq(event.getNewsId()), eq(event.getUserId()), anyString()))
                    .thenReturn(ClaimResult.CLAIMED);
            doThrow(new RuntimeException("redis down")).when(notificationInboxService).markSent(event.getNewsId(), event.getUserId());

            assertDoesNotThrow(() -> service.process(event));

            verify(notificationInboxService, never()).release(any(), any(), any());
        }
    }

    static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("null newsId", mutate(e -> e.setNewsId(null))),
                Arguments.of("null interestTopicId", mutate(e -> e.setInterestTopicId(null))),
                Arguments.of("blank topicName", mutate(e -> e.setTopicName(" "))),
                Arguments.of("blank categoryName", mutate(e -> e.setCategoryName(""))),
                Arguments.of("null newsDate", mutate(e -> e.setNewsDate(null))),
                Arguments.of("blank data", mutate(e -> e.setData(""))),
                Arguments.of("null generatedAt", mutate(e -> e.setGeneratedAt(null))),
                Arguments.of("null userId", mutate(e -> e.setUserId(null))),
                Arguments.of("blank emailAddress", mutate(e -> e.setEmailAddress(""))),
                Arguments.of("malformed emailAddress", mutate(e -> e.setEmailAddress("not-an-email"))));
    }

    private static TopicNewsNotificationEventDTO mutate(Consumer<TopicNewsNotificationEventDTO> mutator) {
        TopicNewsNotificationEventDTO event = validEvent();
        mutator.accept(event);

        return event;
    }

    private static TopicNewsNotificationEventDTO validEvent() {
        return new TopicNewsNotificationEventDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Topic",
                "Category",
                LocalDate.of(2026, 9, 24),
                "<p>Body</p>",
                Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(),
                "recipient@example.com");
    }
}
