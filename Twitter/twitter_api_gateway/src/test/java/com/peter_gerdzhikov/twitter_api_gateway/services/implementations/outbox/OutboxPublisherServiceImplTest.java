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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.NotLeaderOrFollowerException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.KafkaException;
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

    @ParameterizedTest
    @MethodSource("failuresOfTheRowItself")
    void should_count_an_attempt_and_keep_the_row_pending_when_kafka_rejects_the_row(Throwable failure) {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        givenSendFailsWith(failure);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository).save(row);
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_mark_the_row_failed_when_kafka_rejects_it_at_the_last_attempt() {
        Outbox row = pendingRow(MAX_ATTEMPTS - 1, "some-key");
        givenPending(row);
        givenSendFailsWith(new RecordTooLargeException("too large"));

        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxRepository).save(row);
    }

    @ParameterizedTest
    @MethodSource("failuresOfKafkaItself")
    void should_leave_the_row_pending_and_uncounted_when_kafka_cannot_take_it(Throwable failure) {
        Outbox row = pendingRow(MAX_ATTEMPTS - 1, "some-key");
        givenPending(row);
        givenSendFailsWith(failure);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository, never()).save(any());
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_leave_the_row_pending_and_uncounted_when_the_send_times_out() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        publisher.publishPending();

        assertThat(row.getAttempts()).isZero();
        verify(outboxRepository, never()).save(any());
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_never_use_up_the_attempts_of_a_row_however_many_polls_find_kafka_down() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        givenSendFailsWith(new TimeoutException("Topic not present in metadata after 5000 ms."));

        for (int poll = 0; poll < MAX_ATTEMPTS * 4; poll++) {
            publisher.publishPending();
        }

        assertThat(row.getAttempts()).isZero();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void should_stop_the_batch_and_leave_the_later_rows_unsent_when_kafka_cannot_take_one() {
        Outbox failing = pendingRow(0, "failing-key");
        Outbox later = pendingRow(0, "later-key");
        givenPending(failing, later);
        when(kafkaTemplate.send(anyString(), eq("failing-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")));

        int published = publisher.publishPending();

        assertThat(published).isZero();
        verify(kafkaTemplate, never()).send(anyString(), eq("later-key"), anyString());
        verify(outboxRepository, never()).delete(any());
    }

    @Test
    void should_keep_publishing_the_rest_of_the_batch_after_one_row_is_rejected() {
        Outbox rejected = pendingRow(0, "rejected-key");
        Outbox healthy = pendingRow(0, "healthy-key");
        givenPending(rejected, healthy);
        when(kafkaTemplate.send(anyString(), eq("rejected-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("too large")));
        when(kafkaTemplate.send(anyString(), eq("healthy-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        verify(outboxRepository).delete(healthy);
        verify(outboxRepository).save(rejected);
    }

    @Test
    void should_restore_the_interrupt_flag_and_leave_the_row_pending_and_uncounted_when_interrupted() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        publisher.publishPending();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(row.getAttempts()).isZero();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void should_count_an_attempt_when_the_send_itself_throws_because_kafka_rejects_the_row() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Send failed", new InvalidTopicException("Invalid topics: [some topic]")));

        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(1);
        verify(outboxRepository).save(row);
    }

    @Test
    void should_leave_the_row_pending_and_uncounted_when_the_send_itself_throws_because_kafka_is_unreachable() {
        Outbox row = pendingRow(0, "some-key");
        givenPending(row);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Send failed", new TimeoutException("Topic some.topic not present in metadata after 5000 ms.")));

        publisher.publishPending();

        assertThat(row.getAttempts()).isZero();
        verify(outboxRepository, never()).save(any());
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

    private void givenSendFailsWith(Throwable failure) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(failure));
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

    private static Stream<Throwable> failuresOfTheRowItself() {
        return Stream.of(
                new RecordTooLargeException("too large"),
                new RecordBatchTooLargeException("batch too large"),
                new InvalidTopicException("invalid topic"),
                new KafkaException("Send failed", new RecordTooLargeException("too large"))
        );
    }

    private static Stream<Throwable> failuresOfKafkaItself() {
        return Stream.of(
                new TimeoutException("Topic not present in metadata after 5000 ms."),
                new NetworkException("disconnected"),
                new NotLeaderOrFollowerException("leader moved"),
                new TopicAuthorizationException(Set.of("some.topic")),
                new IllegalStateException("broker down")
        );
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> sendResult() {
        return mock(SendResult.class);
    }
}
