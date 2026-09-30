package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.OutboxMessageRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceImplTest {

    private static final int BATCH_SIZE = 7;

    private static final int MAX_ATTEMPTS = 3;

    private OutboxPublisherServiceImpl publisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisherServiceImpl(
                BATCH_SIZE,
                MAX_ATTEMPTS,
                Duration.ofSeconds(1),
                kafkaTemplate,
                outboxMessageRepository
        );
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void should_send_topic_key_and_payload_then_delete_the_message() {
        OutboxMessage message = pendingMessage(0, "some-key");
        givenPending(message);
        when(kafkaTemplate.send("some.topic", "some-key", "{\"a\":1}"))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxMessageRepository).delete(message);
        verify(outboxMessageRepository, never()).save(any());
    }

    @Test
    void should_ask_the_repository_for_one_batch_of_pending_messages() {
        givenPending();

        publisher.publishPending();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxMessageRepository).findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void should_increment_attempts_and_keep_the_message_pending_when_the_send_fails() {
        OutboxMessage message = pendingMessage(0, "some-key");
        givenPending(message);
        givenSendFails();

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxMessageRepository).save(message);
        verify(outboxMessageRepository, never()).delete(any());
    }

    @Test
    void should_mark_the_message_failed_when_the_send_fails_at_the_last_attempt() {
        OutboxMessage message = pendingMessage(MAX_ATTEMPTS - 1, "some-key");
        givenPending(message);
        givenSendFails();

        publisher.publishPending();

        assertThat(message.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxMessageRepository).save(message);
    }

    @Test
    void should_count_a_send_timeout_as_a_failed_attempt() {
        OutboxMessage message = pendingMessage(0, "some-key");
        givenPending(message);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        publisher.publishPending();

        assertThat(message.getAttempts()).isEqualTo(1);
        verify(outboxMessageRepository, never()).delete(any());
    }

    @Test
    void should_keep_publishing_the_rest_of_the_batch_after_one_message_fails() {
        OutboxMessage failing = pendingMessage(0, "failing-key");
        OutboxMessage healthy = pendingMessage(0, "healthy-key");
        givenPending(failing, healthy);
        when(kafkaTemplate.send(anyString(), eq("failing-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(anyString(), eq("healthy-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxMessageRepository).delete(healthy);
        verify(outboxMessageRepository).save(failing);
    }

    @Test
    void should_restore_the_interrupt_flag_and_count_an_attempt_when_interrupted() {
        OutboxMessage message = pendingMessage(0, "some-key");
        givenPending(message);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        publisher.publishPending();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(message.getAttempts()).isEqualTo(1);
    }

    @Test
    void should_do_nothing_when_no_messages_are_pending() {
        givenPending();

        assertThat(publisher.publishPending()).isZero();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private void givenPending(OutboxMessage... messages) {
        when(outboxMessageRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(messages));
    }

    private void givenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
    }

    private OutboxMessage pendingMessage(int attempts, String key) {
        return OutboxMessage
                .builder()
                .id(UUID.randomUUID())
                .topic("some.topic")
                .messageKey(key)
                .payload("{\"a\":1}")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> sendResult() {
        return mock(SendResult.class);
    }
}
