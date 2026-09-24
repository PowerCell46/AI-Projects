package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.EnabledSubscriberProjection;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TopicNewsNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TopicNewsNotificationServiceImpl implements TopicNewsNotificationService {

    /** Sorts before every other {@code uuid} in Postgres, so the first keyset page starts at the very beginning. */
    private static final UUID BEFORE_EVERY_USER_ID = new UUID(0L, 0L);

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final int batchSize;

    private final String notificationRequestedTopicName;

    private final SubscriptionRepository subscriptionRepository;

    private final KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate;

    public TopicNewsNotificationServiceImpl(
            @Value("${app.subscriptions.notification-fanout.batch-size}") int batchSize,
            @Value("${app.kafka.notification-requested.name}") String notificationRequestedTopicName,
            SubscriptionRepository subscriptionRepository,
            KafkaTemplate<String, TopicNewsNotificationEventDTO> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.notificationRequestedTopicName = notificationRequestedTopicName;
        this.subscriptionRepository = subscriptionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void notifySubscribers(TopicNewsEventDTO event) {
        UUID after = BEFORE_EVERY_USER_ID;
        long notified = 0;

        List<EnabledSubscriberProjection> batch = nextBatch(event.getInterestTopicId(), after);
        while (!batch.isEmpty()) {
            batch.forEach(subscriber -> send(event, subscriber));
            notified += batch.size();
            after = batch.getLast().getUserId();
            batch = batch.size() < batchSize ? List.of() : nextBatch(event.getInterestTopicId(), after);
        }

        log.info("Sent {} topic-news notifications for news '{}'.", notified, event.getNewsId());
    }

    private List<EnabledSubscriberProjection> nextBatch(UUID interestTopicId, UUID after) {
        return subscriptionRepository.findEnabledSubscribersAfter(interestTopicId, after, PageRequest.of(0, batchSize));
    }

    private void send(TopicNewsEventDTO event, EnabledSubscriberProjection subscriber) {
        TopicNewsNotificationEventDTO notification = toNotification(event, subscriber);
        try {
            kafkaTemplate
                    .send(notificationRequestedTopicName, subscriber.getUserId().toString(), notification)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while sending a topic-news notification to user '%s'.".formatted(subscriber.getUserId()), e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "Failed to send a topic-news notification to user '%s'.".formatted(subscriber.getUserId()), e);
        }
    }

    private TopicNewsNotificationEventDTO toNotification(TopicNewsEventDTO event, EnabledSubscriberProjection subscriber) {
        return new TopicNewsNotificationEventDTO(
                event.getNewsId(),
                event.getInterestTopicId(),
                event.getTopicName(),
                event.getCategoryName(),
                event.getNewsDate(),
                event.getData(),
                event.getGeneratedAt(),
                subscriber.getUserId(),
                subscriber.getEmail());
    }
}
