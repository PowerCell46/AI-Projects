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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxStatus;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceImplTest {

    private static final int BATCH_SIZE = 7;

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxPublisherServiceImpl publisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

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
    void should_send_topic_key_and_payload_then_delete_the_row() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send("some.topic", "some-key", "{\"a\":1}"))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxRepository).delete(row);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void should_write_a_rejection_back_onto_the_row() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("too large")));

        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository).save(row);
    }

    @Test
    void should_run_inside_a_transaction_for_the_row_locks() throws Exception {
        assertThat(OutboxPublisherServiceImpl.class.getMethod("publishPending").getAnnotation(Transactional.class))
                .isNotNull();
    }

    private void givenPending(Outbox... rows) {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(rows));
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
