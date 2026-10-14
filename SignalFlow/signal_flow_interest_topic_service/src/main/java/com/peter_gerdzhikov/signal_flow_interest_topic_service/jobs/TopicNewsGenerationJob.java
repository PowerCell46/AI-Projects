package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

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
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.NewsHeadlines;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TopicNewsGenerationJob {

    private static final int PAGE_SIZE = 100;

    private final ZoneId newsZone;

    private final int maxConcurrency;

    private final int coveredLookbackDays;

    private final TopicNewsRepository topicNewsRepository;

    private final NewsGenerationService newsGenerationService;

    private final InterestTopicRepository interestTopicRepository;

    public TopicNewsGenerationJob(
            @Value("${app.news.zone}") String newsZone,
            @Value("${app.news.max-concurrency}") int maxConcurrency,
            @Value("${app.news.covered-lookback-days}") int coveredLookbackDays,
            TopicNewsRepository topicNewsRepository,
            NewsGenerationService newsGenerationService,
            InterestTopicRepository interestTopicRepository
    ) {
        this.newsZone = ZoneId.of(newsZone);
        this.maxConcurrency = maxConcurrency;
        this.coveredLookbackDays = coveredLookbackDays;
        this.topicNewsRepository = topicNewsRepository;
        this.newsGenerationService = newsGenerationService;
        this.interestTopicRepository = interestTopicRepository;
    }

    @Scheduled(cron = "${app.news.cron}", zone = "${app.news.zone}")
    public void generateDailyNews() {
        generateNewsFor(LocalDate.now(newsZone));
    }

    public void generateNewsFor(LocalDate newsDate) {
        log.info("Daily news generation job triggered for '{}'.", newsDate);
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));

        // Each topic's OpenRouter call is I/O-bound, so a virtual thread per topic is cheap - the
        // semaphore is what actually bounds how many calls are in flight against OpenRouter at once
        Semaphore concurrencyLimiter = new Semaphore(maxConcurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Page<InterestTopic> page;
            do {
                page = interestTopicRepository.findAll(pageable);
                awaitAll(page
                        .stream()
                        .map(topic -> executor.submit(() -> generateNewsForTopicBounded(topic, newsDate, concurrencyLimiter)))
                        .toList());
                pageable = pageable.next();

            } while (page.hasNext());
        }
    }

    private void generateNewsForTopicBounded(InterestTopic topic, LocalDate newsDate, Semaphore concurrencyLimiter) {
        concurrencyLimiter.acquireUninterruptibly();
        try {
            generateNewsForTopic(topic, newsDate);

        } finally {
            concurrencyLimiter.release();
        }
    }

    private void awaitAll(List<? extends Future<?>> futures) {
        futures.forEach(future -> {
            try {
                future.get();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

            } catch (ExecutionException e) {
                log.error("Unexpected failure generating news for a topic.", e);
            }
        });
    }

    private void generateNewsForTopic(InterestTopic topic, LocalDate newsDate) {
        NewsSchedule schedule = topic.getSchedule();
        if (!schedule.runsOn(newsDate.getDayOfWeek())) {
            log.debug("Interest topic '{}' does not run on '{}' with schedule '{}'; skipping it.",
                    topic.getId(), newsDate, schedule);
            return;
        }

        if (topicNewsRepository.existsByInterestTopic_IdAndNewsDate(topic.getId(), newsDate)) {
            return;
        }

        LocalDate periodStart = schedule.periodStart(newsDate);
        List<String> coveredHeadlines = findCoveredHeadlines(topic, newsDate);
        log.info("Interest topic '{}' has {} already covered headlines for '{}'.",
                topic.getId(), coveredHeadlines.size(), newsDate);

        String data;
        try {
            data = newsGenerationService.generate(topic, newsDate, periodStart, coveredHeadlines);

        } catch (Exception e) {
            log.error("News generation failed for interest topic '{}'; skipping it for '{}'.",
                    topic.getId(), newsDate, e);
            return;
        }

        try {
            topicNewsRepository.saveAndFlush(newPendingNews(topic, newsDate, periodStart, data));

        } catch (DataIntegrityViolationException e) {
            log.warn("Lost the race to insert news for interest topic '{}' on '{}'; another run already did.",
                    topic.getId(), newsDate);
        }
    }

    private List<String> findCoveredHeadlines(InterestTopic topic, LocalDate newsDate) {
        return topicNewsRepository
                .findDataByTopicInDateRange(topic.getId(), newsDate.minusDays(coveredLookbackDays), newsDate)
                .stream()
                .flatMap(data -> NewsHeadlines.extract(data).stream())
                .distinct()
                .toList();
    }

    private TopicNews newPendingNews(InterestTopic topic, LocalDate newsDate, LocalDate periodStart, String data) {
        return TopicNews.builder()
                .interestTopic(topic)
                .newsDate(newsDate)
                .schedule(topic.getSchedule())
                .periodStart(periodStart)
                .data(data)
                .nextAttemptAt(Instant.now())
                .build();
    }
}
