package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceImplTest {

    private static final int BATCH_SIZE = 7;

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisherServiceImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisherServiceImpl(
                BATCH_SIZE,
                MAX_ATTEMPTS,
                Duration.ofSeconds(1),
                outboxRepository,
                kafkaTemplate
        );
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void should_send_topic_key_and_payload_then_delete_the_row() {
        Outbox row = pendingRow(0);
        givenPending(row);
        when(kafkaTemplate.send("some.topic", "some-key", "{\"a\":1}"))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxRepository).delete(row);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void should_ask_the_repository_for_one_batch_of_pending_rows() {
        givenPending();

        publisher.publishPending();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxRepository).findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void should_increment_attempts_and_keep_the_row_pending_when_the_send_fails() {
        Outbox row = pendingRow(0);
        givenPending(row);
        givenSendFails();

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository).save(row);
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_mark_the_row_failed_when_the_send_fails_at_the_last_attempt() {
        Outbox row = pendingRow(MAX_ATTEMPTS - 1);
        givenPending(row);
        givenSendFails();

        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxRepository).save(row);
    }

    @Test
    void should_count_a_send_timeout_as_a_failed_attempt() {
        Outbox row = pendingRow(0);
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());

        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(1);
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_keep_publishing_the_rest_of_the_batch_after_one_row_fails() {
        Outbox failing = pendingRow(0, "failing-key");
        Outbox healthy = pendingRow(0, "healthy-key");
        givenPending(failing, healthy);
        when(kafkaTemplate.send(anyString(), eq("failing-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        when(kafkaTemplate.send(anyString(), eq("healthy-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxRepository).delete(healthy);
        verify(outboxRepository).save(failing);
    }

    @Test
    void should_restore_the_interrupt_flag_and_count_an_attempt_when_interrupted() {
        Outbox row = pendingRow(0);
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        publisher.publishPending();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void should_do_nothing_when_no_rows_are_pending() {
        givenPending();

        assertThat(publisher.publishPending()).isZero();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private void givenPending(Outbox... rows) {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    private void givenSendFails() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("broker down"))));
    }

    private Outbox pendingRow(int attempts) {
        return pendingRow(attempts, "some-key");
    }

    private Outbox pendingRow(int attempts, String key) {
        return Outbox.builder()
                .topic("some.topic")
                .messageKey(key)
                .payload("{\"a\":1}")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .build();
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> sendResult() {
        return mock(SendResult.class);
    }
}
