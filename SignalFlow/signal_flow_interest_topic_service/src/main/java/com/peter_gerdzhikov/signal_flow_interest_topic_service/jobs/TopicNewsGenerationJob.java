package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TopicNewsGenerationJob {

    private static final int PAGE_SIZE = 100;

    private final ZoneId newsZone;

    private final TopicNewsRepository topicNewsRepository;

    private final NewsGenerationService newsGenerationService;

    private final InterestTopicRepository interestTopicRepository;

    public TopicNewsGenerationJob(
            @Value("${app.news.zone}") String newsZone,
            TopicNewsRepository topicNewsRepository,
            NewsGenerationService newsGenerationService,
            InterestTopicRepository interestTopicRepository
    ) {
        this.newsZone = ZoneId.of(newsZone);
        this.topicNewsRepository = topicNewsRepository;
        this.newsGenerationService = newsGenerationService;
        this.interestTopicRepository = interestTopicRepository;
    }

    @Scheduled(cron = "${app.news.cron}", zone = "${app.news.zone}")
    public void generateDailyNews() {
        LocalDate newsDate = LocalDate.now(newsZone);
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));

        Page<InterestTopic> page;
        do {
            page = interestTopicRepository.findAll(pageable);
            page
                .forEach(topic -> generateNewsForTopic(topic, newsDate));
            pageable = pageable.next();

        } while (page.hasNext());
    }

    private void generateNewsForTopic(InterestTopic topic, LocalDate newsDate) {
        if (topicNewsRepository.existsByInterestTopic_IdAndNewsDate(topic.getId(), newsDate)) {
            return;
        }

        String data;
        try {
            data = newsGenerationService.generate(topic, newsDate);

        } catch (Exception e) {
            log.error("News generation failed for interest topic '{}'; skipping it for '{}'.",
                    topic.getId(), newsDate, e);
            return;
        }

        try {
            topicNewsRepository.saveAndFlush(newPendingNews(topic, newsDate, data));

        } catch (DataIntegrityViolationException e) {
            log.warn("Lost the race to insert news for interest topic '{}' on '{}'; another run already did.",
                    topic.getId(), newsDate);
        }
    }

    private TopicNews newPendingNews(InterestTopic topic, LocalDate newsDate, String data) {
        return TopicNews.builder()
                .interestTopic(topic)
                .newsDate(newsDate)
                .data(data)
                .nextAttemptAt(Instant.now())
                .build();
    }
}
