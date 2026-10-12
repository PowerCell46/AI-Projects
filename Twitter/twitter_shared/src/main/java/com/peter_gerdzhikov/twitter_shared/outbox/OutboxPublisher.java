package com.peter_gerdzhikov.twitter_shared.outbox;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends one batch of pending outbox entries through a {@link PendingOutboxStore}. A batch stops at
 * the first failure, so callers run it again on the next tick.
 */
@Slf4j
public class OutboxPublisher {

    /**
     * What Kafka refuses because of the record itself, so sending it again can never work. Anything else (a broker
     * down, a timeout, a leader election, an authorization problem) is not the record's fault and must not use up
     * its attempts.
     */
    private static final List<Class<? extends Throwable>> RECORD_SPECIFIC_FAILURES = List.of(
            RecordTooLargeException.class,
            RecordBatchTooLargeException.class,
            InvalidTopicException.class
    );

    private final int batchSize;

    private final int maxAttempts;

    private final Duration sendTimeout;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            int batchSize,
            int maxAttempts,
            Duration sendTimeout,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.sendTimeout = sendTimeout;
        this.kafkaTemplate = kafkaTemplate;
    }

    public int publishPending(PendingOutboxStore store) {
        List<OutboxRecord> pending = store.loadPending(batchSize);
        int published = 0;

        for (OutboxRecord record : pending) {
            SendOutcome outcome = trySend(record);

            if (outcome == SendOutcome.RETRY_LATER) {
                break;
            }

            if (outcome == SendOutcome.REJECTED) {
                recordRejection(record, store);
                continue;
            }

            store.delete(record);
            published++;
        }

        return published;
    }

    private SendOutcome trySend(OutboxRecord record) {
        try {
            kafkaTemplate
                    .send(record.topic(), record.messageKey(), record.payload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

            return SendOutcome.SENT;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending outbox record {}; it stays pending.", record.id(), ex);

            return SendOutcome.RETRY_LATER;

        } catch (ExecutionException | TimeoutException | KafkaException ex) {
            if (isRecordSpecific(ex)) {
                log.warn("Kafka rejected outbox record {} for topic {}.", record.id(), record.topic(), ex);

                return SendOutcome.REJECTED;
            }

            log.warn("Kafka did not take outbox record {} for topic {}; it and the rest of the batch stay pending.",
                    record.id(), record.topic(), ex);

            return SendOutcome.RETRY_LATER;
        }
    }

    private boolean isRecordSpecific(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            Throwable candidate = current;

            if (RECORD_SPECIFIC_FAILURES.stream().anyMatch(type -> type.isInstance(candidate))) {
                return true;
            }
        }

        return false;
    }

    private void recordRejection(OutboxRecord record, PendingOutboxStore store) {
        record.attempts(record.attempts() + 1);

        if (record.attempts() >= maxAttempts) {
            record.status(OutboxStatus.FAILED);
            log.warn("Outbox record {} was rejected {} times and is marked FAILED until someone requeues it.", record.id(), record.attempts());
        }

        store.save(record);
    }

    private enum SendOutcome {

        SENT,

        RETRY_LATER,

        REJECTED
    }
}
