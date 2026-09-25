package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.NotificationOutboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.NotificationOutboxPublisherService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationOutboxPublisherServiceImpl implements NotificationOutboxPublisherService {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final int batchSize;

    private final int maxAttempts;

    private final String notificationRequestedTopicName;

    private final NotificationOutboxRepository notificationOutboxRepository;

    private final KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate;

    public NotificationOutboxPublisherServiceImpl(
            @Value("${app.subscriptions.notification-fanout.batch-size}") int batchSize,
            @Value("${app.notification-outbox.max-attempts}") int maxAttempts,
            @Value("${app.kafka.notification-requested.name}") String notificationRequestedTopicName,
            NotificationOutboxRepository notificationOutboxRepository,
            KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.notificationRequestedTopicName = notificationRequestedTopicName;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishPendingNotifications() {
        List<NotificationOutbox> pending = notificationOutboxRepository
                .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, batchSize));

        long published = 0;
        for (NotificationOutbox row : pending) {
            if (publish(row)) {
                notificationOutboxRepository.delete(row);
                published++;

            } else {
                bumpAttempt(row);
            }
        }

        log.debug("Notification outbox poll: published {} of {} pending rows.", published, pending.size());
    }

    private boolean publish(NotificationOutbox row) {
        try {
            kafkaTemplate
                    .send(notificationRequestedTopicName, row.getUserId().toString(), toNotification(row))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing notification outbox row '{}'.", row.getId(), e);
            return false;

        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish notification outbox row '{}'.", row.getId(), e);
            return false;
        }
    }

    private void bumpAttempt(NotificationOutbox row) {
        int attempts = row.getAttemptCount() + 1;
        row.setAttemptCount(attempts);

        if (attempts >= maxAttempts) {
            row.setStatus(NotificationOutboxStatus.FAILED);
            log.warn("Notification outbox row '{}' failed permanently after {} attempts.", row.getId(), attempts);
        }

        notificationOutboxRepository.save(row);
    }

    private TopicNewsNotificationEventDTO toNotification(NotificationOutbox row) {
        return new TopicNewsNotificationEventDTO(
                row.getNewsId(),
                row.getInterestTopicId(),
                row.getTopicName(),
                row.getCategoryName(),
                row.getNewsDate(),
                row.getData(),
                row.getGeneratedAt(),
                row.getUserId(),
                row.getEmailAddress());
    }
}
