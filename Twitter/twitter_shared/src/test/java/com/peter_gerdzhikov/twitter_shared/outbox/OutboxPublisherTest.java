package com.peter_gerdzhikov.twitter_shared.outbox;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int BATCH_SIZE = 7;

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private PendingOutboxStore store;

    private OutboxPublisher publisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(
                BATCH_SIZE,
                MAX_ATTEMPTS,
                Duration.ofSeconds(1),
                kafkaTemplate
        );
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void should_send_topic_key_and_payload_then_delete_the_record() {
        TestOutboxRecord record = pendingRecord(0);
        givenPending(record);
        when(kafkaTemplate.send("some.topic", "some-key", "{\"a\":1}"))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending(store);

        assertThat(published).isEqualTo(1);
        verify(store).delete(record);
        verify(store, never()).save(any());
    }

    @Test
    void should_ask_the_store_for_one_batch_of_pending_records() {
        givenPending();

        publisher.publishPending(store);

        verify(store).loadPending(BATCH_SIZE);
    }

    @ParameterizedTest
    @MethodSource("failuresOfTheRecordItself")
    void should_count_an_attempt_and_keep_the_record_pending_when_kafka_rejects_the_record(Throwable failure) {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        givenSendFailsWith(failure);

        int published = publisher.publishPending(store);

        assertThat(published).isZero();
        assertThat(record.attempts()).isEqualTo(1);
        assertThat(record.status()).isEqualTo(OutboxStatus.PENDING);
        verify(store).save(record);
        verify(store, never()).delete(any());
    }

    @Test
    void should_mark_the_record_failed_when_kafka_rejects_it_at_the_last_attempt() {
        TestOutboxRecord record = pendingRecord(MAX_ATTEMPTS - 1, "some-key");
        givenPending(record);
        givenSendFailsWith(new RecordTooLargeException("too large"));

        publisher.publishPending(store);

        assertThat(record.attempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(record.status()).isEqualTo(OutboxStatus.FAILED);
        verify(store).save(record);
    }

    @ParameterizedTest
    @MethodSource("failuresOfKafkaItself")
    void should_leave_the_record_pending_and_uncounted_when_kafka_cannot_take_it(Throwable failure) {
        TestOutboxRecord record = pendingRecord(MAX_ATTEMPTS - 1, "some-key");
        givenPending(record);
        givenSendFailsWith(failure);

        int published = publisher.publishPending(store);

        assertThat(published).isZero();
        assertThat(record.attempts()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(record.status()).isEqualTo(OutboxStatus.PENDING);
        verify(store, never()).save(any());
        verify(store, never()).delete(any());
    }

    @Test
    void should_leave_the_record_pending_and_uncounted_when_the_send_times_out() {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        publisher.publishPending(store);

        assertThat(record.attempts()).isZero();
        verify(store, never()).save(any());
        verify(store, never()).delete(any());
    }

    @Test
    void should_never_use_up_the_attempts_of_a_record_however_many_polls_find_kafka_down() {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        givenSendFailsWith(new TimeoutException("Topic not present in metadata after 5000 ms."));

        for (int poll = 0; poll < MAX_ATTEMPTS * 4; poll++) {
            publisher.publishPending(store);
        }

        assertThat(record.attempts()).isZero();
        assertThat(record.status()).isEqualTo(OutboxStatus.PENDING);
        verify(store, never()).save(any());
    }

    @Test
    void should_stop_the_batch_and_leave_the_later_records_unsent_when_kafka_cannot_take_one() {
        TestOutboxRecord failing = pendingRecord(0, "failing-key");
        TestOutboxRecord later = pendingRecord(0, "later-key");
        givenPending(failing, later);
        when(kafkaTemplate.send(anyString(), eq("failing-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker down")));

        int published = publisher.publishPending(store);

        assertThat(published).isZero();
        verify(kafkaTemplate, never()).send(anyString(), eq("later-key"), anyString());
        verify(store, never()).delete(any());
    }

    @Test
    void should_keep_publishing_the_rest_of_the_batch_after_one_record_is_rejected() {
        TestOutboxRecord rejected = pendingRecord(0, "rejected-key");
        TestOutboxRecord healthy = pendingRecord(0, "healthy-key");
        givenPending(rejected, healthy);
        when(kafkaTemplate.send(anyString(), eq("rejected-key"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("too large")));
        when(kafkaTemplate.send(anyString(), eq("healthy-key"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        int published = publisher.publishPending(store);

        assertThat(published).isEqualTo(1);
        verify(store).delete(healthy);
        verify(store).save(rejected);
    }

    @Test
    void should_restore_the_interrupt_flag_and_leave_the_record_pending_and_uncounted_when_interrupted() {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        publisher.publishPending(store);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(record.attempts()).isZero();
        verify(store, never()).save(any());
    }

    @Test
    void should_count_an_attempt_when_the_send_itself_throws_because_kafka_rejects_the_record() {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Send failed", new InvalidTopicException("Invalid topics: [some topic]")));

        publisher.publishPending(store);

        assertThat(record.attempts()).isEqualTo(1);
        verify(store).save(record);
    }

    @Test
    void should_leave_the_record_pending_and_uncounted_when_the_send_itself_throws_because_kafka_is_unreachable() {
        TestOutboxRecord record = pendingRecord(0, "some-key");
        givenPending(record);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("Send failed", new TimeoutException("Topic some.topic not present in metadata after 5000 ms.")));

        publisher.publishPending(store);

        assertThat(record.attempts()).isZero();
        verify(store, never()).save(any());
    }

    @Test
    void should_do_nothing_when_no_records_are_pending() {
        givenPending();

        assertThat(publisher.publishPending(store)).isZero();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private void givenPending(TestOutboxRecord... records) {
        when(store.loadPending(BATCH_SIZE)).thenReturn(List.of(records));
    }

    private void givenSendFailsWith(Throwable failure) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(failure));
    }

    private TestOutboxRecord pendingRecord(int attempts) {
        return pendingRecord(attempts, "some-key");
    }

    private TestOutboxRecord pendingRecord(int attempts, String key) {
        return new TestOutboxRecord("some.topic", key, "{\"a\":1}", attempts);
    }

    private static Stream<Throwable> failuresOfTheRecordItself() {
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

    private static class TestOutboxRecord implements OutboxRecord {

        private int attempts;

        private final String topic;

        private OutboxStatus status;

        private final String payload;

        private final String messageKey;

        private TestOutboxRecord(String topic, String messageKey, String payload, int attempts) {
            this.topic = topic;
            this.messageKey = messageKey;
            this.payload = payload;
            this.attempts = attempts;
            this.status = OutboxStatus.PENDING;
        }

        @Override
        public Object id() {
            return messageKey;
        }

        @Override
        public String topic() {
            return topic;
        }

        @Override
        public String messageKey() {
            return messageKey;
        }

        @Override
        public String payload() {
            return payload;
        }

        @Override
        public int attempts() {
            return attempts;
        }

        @Override
        public void attempts(int attempts) {
            this.attempts = attempts;
        }

        @Override
        public OutboxStatus status() {
            return status;
        }

        @Override
        public void status(OutboxStatus status) {
            this.status = status;
        }
    }
}
