package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.EnabledSubscriberProjection;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class TopicNewsNotificationServiceImplTest {

    private static final int BATCH_SIZE = 2;

    private static final String TOPIC_NAME = "topic-news.notification-requested";

    private static final UUID BEFORE_EVERY_USER_ID = new UUID(0L, 0L);

    private static final UUID INTEREST_TOPIC_ID = UUID.randomUUID();

    private static final UUID USER_A = UUID.randomUUID();

    private static final UUID USER_B = UUID.randomUUID();

    private static final UUID USER_C = UUID.randomUUID();

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate;

    private TopicNewsNotificationServiceImpl topicNewsNotificationService;

    @BeforeEach
    void setUp() {
        topicNewsNotificationService = new TopicNewsNotificationServiceImpl(
                BATCH_SIZE, TOPIC_NAME, subscriptionRepository, kafkaTemplate);
    }

    @Nested
    class NotifySubscribers {

        @Test
        void should_send_one_notification_with_every_field_mapped_for_an_enabled_subscriber() {
            TopicNewsEventDTO event = newEvent();
            givenSubscriberBatches(List.of(subscriber(USER_A, "a@example.com")));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

            topicNewsNotificationService.notifySubscribers(event);

            ArgumentCaptor<TopicNewsNotificationEventDTO> notificationCaptor =
                    ArgumentCaptor.forClass(TopicNewsNotificationEventDTO.class);
            verify(kafkaTemplate).send(eq(TOPIC_NAME), eq(USER_A.toString()), notificationCaptor.capture());
            TopicNewsNotificationEventDTO notification = notificationCaptor.getValue();
            assertThat(notification.getNewsId()).isEqualTo(event.getNewsId());
            assertThat(notification.getInterestTopicId()).isEqualTo(event.getInterestTopicId());
            assertThat(notification.getTopicName()).isEqualTo(event.getTopicName());
            assertThat(notification.getCategoryName()).isEqualTo(event.getCategoryName());
            assertThat(notification.getNewsDate()).isEqualTo(event.getNewsDate());
            assertThat(notification.getData()).isEqualTo(event.getData());
            assertThat(notification.getGeneratedAt()).isEqualTo(event.getGeneratedAt());
            assertThat(notification.getUserId()).isEqualTo(USER_A);
            assertThat(notification.getEmailAddress()).isEqualTo("a@example.com");
        }

        @Test
        void should_walk_every_batch_of_subscribers() {
            givenSubscriberBatches(
                    List.of(subscriber(USER_A, "a@example.com"), subscriber(USER_B, "b@example.com")),
                    List.of(subscriber(USER_C, "c@example.com")));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

            topicNewsNotificationService.notifySubscribers(newEvent());

            verify(kafkaTemplate).send(eq(TOPIC_NAME), eq(USER_A.toString()), any());
            verify(kafkaTemplate).send(eq(TOPIC_NAME), eq(USER_B.toString()), any());
            verify(kafkaTemplate).send(eq(TOPIC_NAME), eq(USER_C.toString()), any());
        }

        @Test
        void should_send_nothing_when_there_are_no_subscribers() {
            givenSubscriberBatches(List.of());

            topicNewsNotificationService.notifySubscribers(newEvent());

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        void should_propagate_when_a_send_fails() {
            givenSubscriberBatches(List.of(subscriber(USER_A, "a@example.com")));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

            assertThatThrownBy(() -> topicNewsNotificationService.notifySubscribers(newEvent()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /**
     * Stubs consecutive keyset pages for {@link #INTEREST_TOPIC_ID}: the first starts before every user id,
     * each next one after the previous page's last subscriber.
     */
    @SafeVarargs
    private void givenSubscriberBatches(List<EnabledSubscriberProjection>... batches) {
        UUID after = BEFORE_EVERY_USER_ID;
        for (List<EnabledSubscriberProjection> batch : batches) {
            when(subscriptionRepository.findEnabledSubscribersAfter(eq(INTEREST_TOPIC_ID), eq(after), any()))
                    .thenReturn(batch);
            if (!batch.isEmpty()) {
                after = batch.getLast().getUserId();
            }
        }
    }

    private EnabledSubscriberProjection subscriber(UUID userId, String email) {
        EnabledSubscriberProjection subscriber = mock(EnabledSubscriberProjection.class);
        when(subscriber.getUserId()).thenReturn(userId);
        when(subscriber.getEmail()).thenReturn(email);
        return subscriber;
    }

    private TopicNewsEventDTO newEvent() {
        return new TopicNewsEventDTO(
                UUID.randomUUID(),
                INTEREST_TOPIC_ID,
                "rust",
                "programming",
                LocalDate.now(),
                "today's rust news",
                Instant.now());
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, TopicNewsNotificationEventDTO> mockSendResult() {
        return mock(SendResult.class);
    }
}
