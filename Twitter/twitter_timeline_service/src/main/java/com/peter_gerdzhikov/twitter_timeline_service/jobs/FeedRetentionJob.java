package com.peter_gerdzhikov.twitter_timeline_service.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedRetentionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedRetentionJob {

    private final FeedRetentionService feedRetentionService;

    @Scheduled(
            cron = "${app.feed.cleanup.cron}",
            zone = "${app.feed.cleanup.zone}"
    )
    public void deleteExpiredFeedEntries() {
        int deleted = feedRetentionService.deleteExpiredEntries();

        log.info("Deleted {} feed entries past the retention window.", deleted);
    }
}
