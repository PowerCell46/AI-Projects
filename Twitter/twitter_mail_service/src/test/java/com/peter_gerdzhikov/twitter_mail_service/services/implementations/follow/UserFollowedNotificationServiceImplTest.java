package com.peter_gerdzhikov.twitter_mail_service.services.implementations.follow;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery.MailDispatchServiceImpl;
import com.peter_gerdzhikov.twitter_mail_service.services.implementations.inbox.MailEventValidationServiceImpl;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.ClaimResult;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.MailDeliveryService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.MailInboxService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowedNotificationServiceImplTest {

    private static final Duration WINDOW = Duration.ofHours(24);

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-01T12:00:00Z");

    private static final Instant NOW = OCCURRED_AT.plusSeconds(60);

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private MailInboxService mailInboxService;

    @Mock
    private MailDeliveryService mailDeliveryService;

    private UserFollowedNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserFollowedNotificationServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC),
                WINDOW,
                new MailEventValidationServiceImpl(VALIDATOR),
                new MailDispatchServiceImpl(mailInboxService, mailDeliveryService),
                new FollowEmailRenderer("http://localhost:5173"));
    }

    @Nested
    class Process {

        @Test
        void should_claim_the_pair_key_then_send_to_the_followee_then_mark_the_pair_sent_for_the_window() {
            UserFollowedEventDTO event = anEvent(UUID.randomUUID(), UUID.randomUUID());
            String key = "mail:followed:" + event.getFollowerId() + ":" + event.getFolloweeId();
            when(mailInboxService.claim(eq(key), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            InOrder inOrder = inOrder(mailInboxService, mailDeliveryService);
            inOrder.verify(mailInboxService).claim(eq(key), anyString());
            inOrder.verify(mailDeliveryService).send(
                    eq("bob@example.com"),
                    eq("ana followed you"),
                    argThat(html -> html.contains("http://localhost:5173/feed")),
                    argThat(text -> text.contains("ana (@ana) is now following you.")));
            inOrder.verify(mailInboxService).markSent(key, WINDOW);
        }

        @Test
        void should_claim_the_same_key_when_the_same_pair_follows_again_with_a_new_event_id() {
            UUID followerId = UUID.randomUUID();
            UUID followeeId = UUID.randomUUID();
            String key = "mail:followed:" + followerId + ":" + followeeId;
            when(mailInboxService.claim(eq(key), anyString())).thenReturn(ClaimResult.CLAIMED, ClaimResult.ALREADY_SENT);

            service.process(anEvent(followerId, followeeId));
            service.process(anEvent(followerId, followeeId));

            verify(mailDeliveryService).send(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void should_claim_a_different_key_when_the_pair_is_reversed() {
            UUID followerId = UUID.randomUUID();
            UUID followeeId = UUID.randomUUID();
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(anEvent(followerId, followeeId));
            service.process(anEvent(followeeId, followerId));

            verify(mailInboxService).claim(eq("mail:followed:" + followerId + ":" + followeeId), anyString());
            verify(mailInboxService).claim(eq("mail:followed:" + followeeId + ":" + followerId), anyString());
        }

        @Test
        void should_send_nothing_when_the_pair_was_already_sent_inside_the_window() {
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.ALREADY_SENT);

            service.process(anEvent(UUID.randomUUID(), UUID.randomUUID()));

            verifyNoInteractions(mailDeliveryService);
            verify(mailInboxService, never()).markSent(anyString(), eq(WINDOW));
        }

        @Test
        void should_skip_without_touching_the_inbox_when_the_follow_is_older_than_the_window() {
            UserFollowedEventDTO event = anEvent(UUID.randomUUID(), UUID.randomUUID());
            event.setOccurredAt(NOW.minus(WINDOW).minusSeconds(1));

            service.process(event);

            verifyNoInteractions(mailInboxService, mailDeliveryService);
        }

        @Test
        void should_send_when_the_follow_is_exactly_one_window_old() {
            UserFollowedEventDTO event = anEvent(UUID.randomUUID(), UUID.randomUUID());
            event.setOccurredAt(NOW.minus(WINDOW));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            verify(mailDeliveryService).send(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void should_send_when_the_follow_is_in_the_future_because_of_clock_skew() {
            UserFollowedEventDTO event = anEvent(UUID.randomUUID(), UUID.randomUUID());
            event.setOccurredAt(NOW.plusSeconds(30));
            when(mailInboxService.claim(anyString(), anyString())).thenReturn(ClaimResult.CLAIMED);

            service.process(event);

            verify(mailDeliveryService).send(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -86_400})
        void should_fail_to_start_when_the_follow_window_is_zero_or_negative(long windowSeconds) {
            Duration window = Duration.ofSeconds(windowSeconds);

            assertThrows(IllegalStateException.class, () -> new UserFollowedNotificationServiceImpl(
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    window,
                    new MailEventValidationServiceImpl(VALIDATOR),
                    new MailDispatchServiceImpl(mailInboxService, mailDeliveryService),
                    new FollowEmailRenderer("http://localhost:5173")));
        }
    }

    @Nested
    class Validate {

        @Test
        void should_throw_and_touch_nothing_when_the_event_is_invalid() {
            UserFollowedEventDTO invalidEvent = anEvent(UUID.randomUUID(), UUID.randomUUID());
            invalidEvent.setFollowerId(null);

            assertThrows(InvalidMailEventException.class, () -> service.process(invalidEvent));

            verifyNoInteractions(mailInboxService, mailDeliveryService);
        }
    }

    private static UserFollowedEventDTO anEvent(UUID followerId, UUID followeeId) {
        return UserFollowedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .followerId(followerId)
                .followeeId(followeeId)
                .occurredAt(OCCURRED_AT)
                .followeeEmail("bob@example.com")
                .followerUsername("ana")
                .followeeUsername("bob")
                .build();
    }
}
