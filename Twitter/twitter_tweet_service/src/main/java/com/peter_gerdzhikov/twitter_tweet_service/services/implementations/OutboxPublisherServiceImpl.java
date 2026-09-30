package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxPublisherService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

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
            if (!trySend(message)) {
                recordFailure(message);
                continue;
            }

            outboxMessageRepository.delete(message);
            published++;
        }

        return published;
    }

    private boolean trySend(OutboxMessage message) {
        try {
            kafkaTemplate
                    .send(message.getTopic(), message.getMessageKey(), message.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending outbox message {}.", message.getId(), e);

            return false;

        } catch (ExecutionException | TimeoutException e) {
            log.warn("Sending outbox message {} to topic {} failed.", message.getId(), message.getTopic(), e);

            return false;
        }
    }

    private void recordFailure(OutboxMessage message) {
        message.setAttempts(message.getAttempts() + 1);

        if (message.getAttempts() >= maxAttempts) {
            message.setStatus(OutboxStatus.FAILED);
            log.warn("Outbox message {} failed {} times and is left for manual inspection.", message.getId(), message.getAttempts());
        }

        outboxMessageRepository.save(message);
    }
}
