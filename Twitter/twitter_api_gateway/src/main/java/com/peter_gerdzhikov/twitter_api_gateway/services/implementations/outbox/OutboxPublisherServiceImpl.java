package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox.OutboxPublisherService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private final int batchSize;

    private final int maxAttempts;

    private final Duration sendTimeout;

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherServiceImpl(
            @Value("${app.outbox.batch-size}") int batchSize,
            @Value("${app.outbox.max-attempts}") int maxAttempts,
            @Value("${app.outbox.send-timeout}") Duration sendTimeout,
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.sendTimeout = sendTimeout;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public int publishPending() {
        List<Outbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );
        int published = 0;

        for (Outbox row : pending) {
            if (!trySend(row)) {
                recordFailure(row);
                continue;
            }

            outboxRepository.delete(row);
            published++;
        }

        return published;
    }

    private boolean trySend(Outbox row) {
        try {
            kafkaTemplate
                    .send(row.getTopic(), row.getMessageKey(), row.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending outbox row {}.", row.getId(), e);
            return false;

        } catch (ExecutionException | TimeoutException e) {
            log.warn("Sending outbox row {} to topic {} failed.", row.getId(), row.getTopic(), e);
            return false;
        }
    }

    private void recordFailure(Outbox row) {
        row.setAttempts(row.getAttempts() + 1);

        if (row.getAttempts() >= maxAttempts) {
            row.setStatus(OutboxStatus.FAILED);
            log.warn("Outbox row {} failed {} times and is left for manual inspection.", row.getId(), row.getAttempts());
        }

        outboxRepository.save(row);
    }
}
