package com.peter_gerdzhikov.twitter_tweet_service.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxPublisherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private final OutboxPublisherService outboxPublisherService;

    // The initial delay equals the poll delay so a test profile with a huge delay never fires at startup.
    @Scheduled(
            fixedDelayString = "${app.outbox.poll-fixed-delay-ms}",
            initialDelayString = "${app.outbox.poll-fixed-delay-ms}"
    )
    public void publishPending() {
        int published = outboxPublisherService.publishPending();

        if (published > 0) {
            log.info("Published {} outbox messages to Kafka.", published);
        }
    }
}
