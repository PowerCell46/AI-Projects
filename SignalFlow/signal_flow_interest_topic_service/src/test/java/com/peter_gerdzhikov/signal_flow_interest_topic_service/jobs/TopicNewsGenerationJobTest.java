package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;

@ExtendWith(MockitoExtension.class)
class TopicNewsGenerationJobTest {

    private static final String NEWS_ZONE = "UTC";

    private static final String TOPIC_PROMPT = "What's new with Rust?";

    @Mock
    private TopicNewsRepository topicNewsRepository;

    @Mock
    private NewsGenerationService newsGenerationService;

    @Mock
    private InterestTopicRepository interestTopicRepository;

    private TopicNewsGenerationJob topicNewsGenerationJob;

    @BeforeEach
    void setUp() {
        topicNewsGenerationJob = new TopicNewsGenerationJob(
                NEWS_ZONE, topicNewsRepository, newsGenerationService, interestTopicRepository);
    }

    @Nested
    class GenerateDailyNews {

        @Test
        void should_skip_a_topic_when_news_already_exists_for_today() {
            InterestTopic topic = newTopic();
            when(interestTopicRepository.findAll(any(Pageable.class))).thenReturn(singlePage(topic));
            when(topicNewsRepository.existsByInterestTopic_IdAndNewsDate(eq(topic.getId()), any(LocalDate.class)))
                    .thenReturn(true);

            topicNewsGenerationJob.generateDailyNews();

            verify(newsGenerationService, never()).generate(any());
            verify(topicNewsRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_save_a_pending_news_row_when_none_exists_for_today() {
            InterestTopic topic = newTopic();
            when(interestTopicRepository.findAll(any(Pageable.class))).thenReturn(singlePage(topic));
            when(topicNewsRepository.existsByInterestTopic_IdAndNewsDate(eq(topic.getId()), any(LocalDate.class)))
                    .thenReturn(false);
            when(newsGenerationService.generate(topic)).thenReturn("today's rust news");

            topicNewsGenerationJob.generateDailyNews();

            ArgumentCaptor<TopicNews> newsCaptor = ArgumentCaptor.forClass(TopicNews.class);
            verify(topicNewsRepository).saveAndFlush(newsCaptor.capture());
            TopicNews saved = newsCaptor.getValue();
            assertThat(saved.getInterestTopic()).isEqualTo(topic);
            assertThat(saved.getData()).isEqualTo("today's rust news");
            assertThat(saved.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(saved.getNewsDate()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
            assertThat(saved.getNextAttemptAt()).isNotNull();
        }

        @Test
        void should_skip_the_failing_topic_and_still_save_the_rest() {
            InterestTopic failingTopic = newTopic();
            InterestTopic healthyTopic = newTopic();
            when(interestTopicRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(failingTopic, healthyTopic)));
            when(topicNewsRepository.existsByInterestTopic_IdAndNewsDate(any(), any(LocalDate.class)))
                    .thenReturn(false);
            when(newsGenerationService.generate(failingTopic)).thenThrow(new RuntimeException("AI timeout"));
            when(newsGenerationService.generate(healthyTopic)).thenReturn("healthy topic news");

            assertThatCode(() -> topicNewsGenerationJob.generateDailyNews()).doesNotThrowAnyException();

            verify(topicNewsRepository, times(1)).saveAndFlush(any());
        }

        @Test
        void should_swallow_a_unique_constraint_race_on_save() {
            InterestTopic topic = newTopic();
            when(interestTopicRepository.findAll(any(Pageable.class))).thenReturn(singlePage(topic));
            when(topicNewsRepository.existsByInterestTopic_IdAndNewsDate(eq(topic.getId()), any(LocalDate.class)))
                    .thenReturn(false);
            when(newsGenerationService.generate(topic)).thenReturn("today's rust news");
            when(topicNewsRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatCode(() -> topicNewsGenerationJob.generateDailyNews()).doesNotThrowAnyException();
        }

        @Test
        void should_page_through_every_page_of_topics() {
            InterestTopic firstTopic = newTopic();
            InterestTopic secondTopic = newTopic();
            Page<InterestTopic> firstPage = new PageImpl<>(List.of(firstTopic), PageRequest.of(0, 1), 2);
            Page<InterestTopic> secondPage = new PageImpl<>(List.of(secondTopic), PageRequest.of(1, 1), 2);
            when(interestTopicRepository.findAll(any(Pageable.class))).thenReturn(firstPage, secondPage);
            when(topicNewsRepository.existsByInterestTopic_IdAndNewsDate(any(), any(LocalDate.class)))
                    .thenReturn(true);

            topicNewsGenerationJob.generateDailyNews();

            verify(interestTopicRepository, times(2)).findAll(any(Pageable.class));
            verify(topicNewsRepository).existsByInterestTopic_IdAndNewsDate(eq(firstTopic.getId()), any());
            verify(topicNewsRepository).existsByInterestTopic_IdAndNewsDate(eq(secondTopic.getId()), any());
        }
    }

    private InterestTopic newTopic() {
        InterestTopic topic = new InterestTopic();
        topic.setId(UUID.randomUUID());
        topic.setName("rust");
        topic.setPrompt(TOPIC_PROMPT);
        return topic;
    }

    private Page<InterestTopic> singlePage(InterestTopic topic) {
        return new PageImpl<>(List.of(topic));
    }
}
