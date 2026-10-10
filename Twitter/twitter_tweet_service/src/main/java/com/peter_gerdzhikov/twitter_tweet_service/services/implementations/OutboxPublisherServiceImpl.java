package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxPublisherService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    /**
     * What Kafka refuses because of the message itself, so sending it again can never work. Anything else (a broker
     * down, a timeout, a leader election, an authorization problem) is not the message's fault and must not use up
     * its attempts.
     */
    private static final List<Class<? extends Throwable>> MESSAGE_SPECIFIC_FAILURES = List.of(
            RecordTooLargeException.class,
            RecordBatchTooLargeException.class,
            InvalidTopicException.class
    );

    private final int batchSize;

    private final int maxAttempts;

    private final Duration sendTimeout;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final OutboxMessageRepository outboxMessageRepository;

    public OutboxPublisherServiceImpl(
            @Value("${app.outbox.batch-size}") int batchSize,
            @Value("${app.outbox.max-attempts}") int maxAttempts,
            @Value("${app.outbox.send-timeout}") Duration sendTimeout,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxMessageRepository outboxMessageRepository
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.sendTimeout = sendTimeout;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxMessageRepository = outboxMessageRepository;
    }

    @Override
    public int publishPending() {
        List<OutboxMessage> pending = outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );
        int published = 0;

        for (OutboxMessage message : pending) {
            SendOutcome outcome = trySend(message);

            if (outcome == SendOutcome.RETRY_LATER) {
                break;
            }

            if (outcome == SendOutcome.REJECTED) {
                recordRejection(message);
                continue;
            }

            outboxMessageRepository.delete(message);
            published++;
        }

        return published;
    }

    private SendOutcome trySend(OutboxMessage message) {
        try {
            kafkaTemplate
                    .send(message.getTopic(), message.getMessageKey(), message.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

            return SendOutcome.SENT;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending outbox message {}; it stays pending.", message.getId(), e);

            return SendOutcome.RETRY_LATER;

        } catch (ExecutionException | TimeoutException | KafkaException e) {
            if (isMessageSpecific(e)) {
                log.warn("Kafka rejected outbox message {} for topic {}.", message.getId(), message.getTopic(), e);

                return SendOutcome.REJECTED;
            }

            log.warn("Kafka did not take outbox message {} for topic {}; it and the rest of the batch stay pending.",
                    message.getId(), message.getTopic(), e);

            return SendOutcome.RETRY_LATER;
        }
    }

    private boolean isMessageSpecific(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            Throwable candidate = current;

            if (MESSAGE_SPECIFIC_FAILURES.stream().anyMatch(type -> type.isInstance(candidate))) {
                return true;
            }
        }

        return false;
    }

    private void recordRejection(OutboxMessage message) {
        message.setAttempts(message.getAttempts() + 1);

        if (message.getAttempts() >= maxAttempts) {
            message.setStatus(OutboxStatus.FAILED);
            log.warn("Outbox message {} was rejected {} times and is marked FAILED until someone requeues it.", message.getId(), message.getAttempts());
        }

        outboxMessageRepository.save(message);
    }

    private enum SendOutcome {

        SENT,

        RETRY_LATER,

        REJECTED
    }
}
