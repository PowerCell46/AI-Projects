package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.NotificationOutboxRepository;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherServiceImplTest {

    private static final int BATCH_SIZE = 10;

    private static final int MAX_ATTEMPTS = 3;

    private static final String TOPIC_NAME = "topic-news.notification-requested";

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate;

    private NotificationOutboxPublisherServiceImpl notificationOutboxPublisherService;

    @BeforeEach
    void setUp() {
        notificationOutboxPublisherService = new NotificationOutboxPublisherServiceImpl(
                BATCH_SIZE, MAX_ATTEMPTS, TOPIC_NAME, notificationOutboxRepository, kafkaTemplate);
    }

    @Nested
    class PublishPendingNotifications {

        @Test
        void should_publish_and_delete_a_row_on_a_successful_send() {
            NotificationOutbox row = pendingRow();
            givenPendingRows(row);
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

            notificationOutboxPublisherService.publishPendingNotifications();

            ArgumentCaptor<TopicNewsNotificationEventDTO> notificationCaptor =
                    ArgumentCaptor.forClass(TopicNewsNotificationEventDTO.class);
            verify(kafkaTemplate).send(eq(TOPIC_NAME), eq(row.getUserId().toString()), notificationCaptor.capture());
            TopicNewsNotificationEventDTO notification = notificationCaptor.getValue();
            assertThat(notification.getNewsId()).isEqualTo(row.getNewsId());
            assertThat(notification.getInterestTopicId()).isEqualTo(row.getInterestTopicId());
            assertThat(notification.getTopicName()).isEqualTo(row.getTopicName());
            assertThat(notification.getCategoryName()).isEqualTo(row.getCategoryName());
            assertThat(notification.getNewsDate()).isEqualTo(row.getNewsDate());
            assertThat(notification.getData()).isEqualTo(row.getData());
            assertThat(notification.getGeneratedAt()).isEqualTo(row.getGeneratedAt());
            assertThat(notification.getUserId()).isEqualTo(row.getUserId());
            assertThat(notification.getEmailAddress()).isEqualTo(row.getEmailAddress());
            verify(notificationOutboxRepository).delete(row);
            verify(notificationOutboxRepository, never()).save(any());
        }

        @Test
        void should_increment_the_attempt_count_and_stay_pending_below_the_attempt_cap() {
            NotificationOutbox row = pendingRow();
            givenPendingRows(row);
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

            notificationOutboxPublisherService.publishPendingNotifications();

            assertThat(row.getAttemptCount()).isEqualTo(1);
            assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
            verify(notificationOutboxRepository).save(row);
            verify(notificationOutboxRepository, never()).delete(any());
        }

        @Test
        void should_flip_to_failed_on_the_final_allowed_attempt() {
            NotificationOutbox row = pendingRow();
            row.setAttemptCount(MAX_ATTEMPTS - 1);
            givenPendingRows(row);
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

            notificationOutboxPublisherService.publishPendingNotifications();

            assertThat(row.getAttemptCount()).isEqualTo(MAX_ATTEMPTS);
            assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
            verify(notificationOutboxRepository).save(row);
        }

        @Test
        void should_continue_publishing_the_remaining_rows_after_one_fails() {
            NotificationOutbox failing = pendingRow();
            NotificationOutbox succeeding = pendingRow();
            givenPendingRows(failing, succeeding);
            when(kafkaTemplate.send(eq(TOPIC_NAME), eq(failing.getUserId().toString()), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));
            when(kafkaTemplate.send(eq(TOPIC_NAME), eq(succeeding.getUserId().toString()), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

            notificationOutboxPublisherService.publishPendingNotifications();

            verify(notificationOutboxRepository).save(failing);
            verify(notificationOutboxRepository).delete(succeeding);
        }

        @Test
        void should_do_nothing_when_there_are_no_pending_rows() {
            givenPendingRows();

            notificationOutboxPublisherService.publishPendingNotifications();

            verifyNoInteractions(kafkaTemplate);
        }
    }

    private void givenPendingRows(NotificationOutbox... rows) {
        when(notificationOutboxRepository.findByStatusOrderByCreatedAtAsc(
                eq(NotificationOutboxStatus.PENDING), eq(PageRequest.of(0, BATCH_SIZE))))
                .thenReturn(List.of(rows));
    }

    private NotificationOutbox pendingRow() {
        return NotificationOutbox
                .builder()
                .newsId(UUID.randomUUID())
                .interestTopicId(UUID.randomUUID())
                .topicName("rust")
                .categoryName("programming")
                .newsDate(LocalDate.now())
                .data("today's rust news")
                .generatedAt(Instant.now())
                .userId(UUID.randomUUID())
                .emailAddress("bob@example.com")
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, TopicNewsNotificationEventDTO> mockSendResult() {
        return mock(SendResult.class);
    }
}
