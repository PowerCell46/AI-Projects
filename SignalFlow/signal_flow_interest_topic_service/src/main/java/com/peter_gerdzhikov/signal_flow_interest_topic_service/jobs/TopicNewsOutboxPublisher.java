package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TopicNewsOutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final int batchSize;

    private final int maxAttempts;

    private final String topicName;

    private final Duration baseBackoff;

    private final TopicNewsRepository topicNewsRepository;

    private final KafkaTemplate<String, TopicNewsEventDTO> kafkaTemplate;

    public TopicNewsOutboxPublisher(
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts,
            @Value("${app.kafka.topic-news.name:topic-news.generated}") String topicName,
            @Value("${app.outbox.base-backoff:PT1M}") Duration baseBackoff,
            TopicNewsRepository topicNewsRepository,
            KafkaTemplate<String, TopicNewsEventDTO> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.topicName = topicName;
        this.baseBackoff = baseBackoff;
        this.topicNewsRepository = topicNewsRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT10S}")
    public void publishDue() {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<TopicNews> due = topicNewsRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        NewsStatus.PENDING,
                        Instant.now(),
                        pageable
                );

        due
            .forEach(this::publishOneSafely);
    }

    private void publishOneSafely(TopicNews news) {
        try {
            publishOne(news);

        } catch (Exception e) {
            log.error("Unexpected error publishing news '{}'; skipping it this cycle.", news.getId(), e);
        }
    }

    private void publishOne(TopicNews news) {
        TopicNewsEventDTO event = toEvent(news);

        try {
            kafkaTemplate
                    .send(topicName, event.getInterestTopicId().toString(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            markFailed(news, cause);
            return;
        }

        markSent(news);
        log.info("Published news '{}' for interest topic '{}'.", news.getId(), event.getInterestTopicId());
    }

    private void markSent(TopicNews news) {
        news.setStatus(NewsStatus.SENT);
        news.setSentAt(Instant.now());
        topicNewsRepository.saveAndFlush(news);
    }

    private void markFailed(TopicNews news, Throwable error) {
        int attempts = news.getAttempts() + 1;
        news.setAttempts(attempts);
        news.setLastError(error.getMessage());

        if (attempts >= maxAttempts) {
            news.setStatus(NewsStatus.FAILED);
            log.error("Giving up publishing news '{}' after {} attempts.", news.getId(), attempts, error);

        } else {
            news.setNextAttemptAt(Instant.now().plus(baseBackoff.multipliedBy(1L << (attempts - 1))));
            log.warn("Failed to publish news '{}' on attempt {}; retrying at '{}'.",
                    news.getId(), attempts, news.getNextAttemptAt(), error);
        }

        topicNewsRepository.saveAndFlush(news);
    }

    private TopicNewsEventDTO toEvent(TopicNews news) {
        InterestTopic topic = news.getInterestTopic();
        return new TopicNewsEventDTO(
                news.getId(),
                topic.getId(),
                topic.getName(),
                topic.getCategory().getName(),
                news.getNewsDate(),
                news.getData(),
                news.getCreatedAt());
    }
}
