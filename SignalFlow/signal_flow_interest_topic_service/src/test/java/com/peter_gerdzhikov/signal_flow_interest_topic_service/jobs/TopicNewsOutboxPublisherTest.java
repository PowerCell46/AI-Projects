package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;

@ExtendWith(MockitoExtension.class)
class TopicNewsOutboxPublisherTest {

    private static final int BATCH_SIZE = 50;

    private static final int MAX_ATTEMPTS = 5;

    private static final String TOPIC_NAME = "topic-news.generated";

    private static final Duration BASE_BACKOFF = Duration.ofMinutes(1);

    @Mock
    private TopicNewsRepository topicNewsRepository;

    @Mock
    private KafkaTemplate<String, TopicNewsEventDTO> kafkaTemplate;

    private TopicNewsOutboxPublisher topicNewsOutboxPublisher;

    @BeforeEach
    void setUp() {
        topicNewsOutboxPublisher = new TopicNewsOutboxPublisher(
                BATCH_SIZE, MAX_ATTEMPTS, TOPIC_NAME, BASE_BACKOFF, topicNewsRepository, kafkaTemplate);
    }

    @Nested
    class PublishDue {

        @Test
        void should_mark_news_as_sent_when_the_send_succeeds() {
            TopicNews news = newPendingNews();
            when(topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                    eq(NewsStatus.PENDING), any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(news));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

            topicNewsOutboxPublisher.publishDue();

            ArgumentCaptor<TopicNewsEventDTO> eventCaptor = ArgumentCaptor.forClass(TopicNewsEventDTO.class);
            verify(kafkaTemplate).send(
                    eq(TOPIC_NAME),
                    eq(news.getInterestTopic().getId().toString()),
                    eventCaptor.capture());
            TopicNewsEventDTO event = eventCaptor.getValue();
            assertThat(event.getNewsId()).isEqualTo(news.getId());
            assertThat(event.getInterestTopicId()).isEqualTo(news.getInterestTopic().getId());
            assertThat(event.getTopicName()).isEqualTo("rust");
            assertThat(event.getCategoryName()).isEqualTo("programming");
            assertThat(event.getNewsDate()).isEqualTo(news.getNewsDate());
            assertThat(event.getData()).isEqualTo(news.getData());
            assertThat(event.getGeneratedAt()).isEqualTo(news.getCreatedAt());

            ArgumentCaptor<TopicNews> savedCaptor = ArgumentCaptor.forClass(TopicNews.class);
            verify(topicNewsRepository).saveAndFlush(savedCaptor.capture());
            TopicNews saved = savedCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NewsStatus.SENT);
            assertThat(saved.getSentAt()).isNotNull();
        }

        @Test
        void should_back_off_exponentially_when_the_send_fails_before_reaching_max_attempts() {
            TopicNews news = newPendingNews();
            news.setAttempts(1);
            when(topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                    eq(NewsStatus.PENDING), any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(news));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

            Instant beforeRetry = Instant.now();
            topicNewsOutboxPublisher.publishDue();

            ArgumentCaptor<TopicNews> savedCaptor = ArgumentCaptor.forClass(TopicNews.class);
            verify(topicNewsRepository).saveAndFlush(savedCaptor.capture());
            TopicNews saved = savedCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(saved.getAttempts()).isEqualTo(2);
            assertThat(saved.getLastError()).isEqualTo("broker unreachable");
            assertThat(saved.getNextAttemptAt()).isAfterOrEqualTo(beforeRetry.plus(BASE_BACKOFF.multipliedBy(2)));
        }

        @Test
        void should_mark_news_as_failed_when_the_send_fails_on_the_final_attempt() {
            TopicNews news = newPendingNews();
            news.setAttempts(MAX_ATTEMPTS - 1);
            when(topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                    eq(NewsStatus.PENDING), any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(news));
            when(kafkaTemplate.send(eq(TOPIC_NAME), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

            topicNewsOutboxPublisher.publishDue();

            ArgumentCaptor<TopicNews> savedCaptor = ArgumentCaptor.forClass(TopicNews.class);
            verify(topicNewsRepository).saveAndFlush(savedCaptor.capture());
            TopicNews saved = savedCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NewsStatus.FAILED);
            assertThat(saved.getAttempts()).isEqualTo(MAX_ATTEMPTS);
            assertThat(saved.getLastError()).isEqualTo("broker unreachable");
        }
    }

    private TopicNews newPendingNews() {
        Category category = new Category();
        category.setName("programming");

        InterestTopic topic = new InterestTopic();
        topic.setId(UUID.randomUUID());
        topic.setName("rust");
        topic.setCategory(category);

        TopicNews news = new TopicNews();
        news.setId(UUID.randomUUID());
        news.setInterestTopic(topic);
        news.setNewsDate(LocalDate.now());
        news.setData("today's rust news");
        news.setStatus(NewsStatus.PENDING);
        news.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        news.setNextAttemptAt(Instant.now());
        return news;
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, TopicNewsEventDTO> mockSendResult() {
        return mock(SendResult.class);
    }
}
