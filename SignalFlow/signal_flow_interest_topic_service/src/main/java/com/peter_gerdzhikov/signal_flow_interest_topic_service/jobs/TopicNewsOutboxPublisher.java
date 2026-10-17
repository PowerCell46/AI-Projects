package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.projections.TopicCategoryNameProjection;

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

    private final InterestTopicRepository interestTopicRepository;

    private final KafkaTemplate<String, TopicNewsEventDTO> kafkaTemplate;

    public TopicNewsOutboxPublisher(
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.max-attempts:5}") int maxAttempts,
            @Value("${app.kafka.topic-news.name:topic-news.generated}") String topicName,
            @Value("${app.outbox.base-backoff:PT1M}") Duration baseBackoff,
            TopicNewsRepository topicNewsRepository,
            InterestTopicRepository interestTopicRepository,
            KafkaTemplate<String, TopicNewsEventDTO> kafkaTemplate
    ) {
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.topicName = topicName;
        this.baseBackoff = baseBackoff;
        this.topicNewsRepository = topicNewsRepository;
        this.interestTopicRepository = interestTopicRepository;
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

        Map<UUID, List<String>> categoryNamesByTopicId = findCategoryNamesByTopicId(due);

        due
                .forEach(news -> publishOneSafely(news, categoryNamesByTopicId));
    }

    private void publishOneSafely(TopicNews news, Map<UUID, List<String>> categoryNamesByTopicId) {
        try {
            publishOne(news, categoryNamesByTopicId);

        } catch (Exception e) {
            log.error("Unexpected error publishing news '{}'; skipping it this cycle.", news.getId(), e);
        }
    }

    private void publishOne(TopicNews news, Map<UUID, List<String>> categoryNamesByTopicId) {
        TopicNewsEventDTO event = toEvent(news, categoryNamesByTopicId);

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

    private Map<UUID, List<String>> findCategoryNamesByTopicId(List<TopicNews> due) {
        List<UUID> topicIds = due
                .stream()
                .map(news -> news.getInterestTopic().getId())
                .toList();
        if (topicIds.isEmpty()) {
            return Map.of();
        }

        return interestTopicRepository
                .findCategoryNamesByTopicIds(topicIds)
                .stream()
                .collect(Collectors.groupingBy(
                                TopicCategoryNameProjection::getTopicId,
                                Collectors.mapping(
                                        TopicCategoryNameProjection::getCategoryName,
                                        Collectors.collectingAndThen(Collectors.toList(), TopicNewsOutboxPublisher::sorted)
                                )
                        )
                );
    }

    private static List<String> sorted(List<String> names) {
        return names
                .stream()
                .sorted()
                .toList();
    }

    private TopicNewsEventDTO toEvent(TopicNews news, Map<UUID, List<String>> categoryNamesByTopicId) {
        InterestTopic topic = news.getInterestTopic();
        return new TopicNewsEventDTO(
                news.getId(),
                topic.getId(),
                topic.getName(),
                categoryNamesByTopicId.get(topic.getId()),
                news.getNewsDate(),
                news.getSchedule(),
                news.getPeriodStart(),
                news.getData(),
                news.getCreatedAt());
    }
}
