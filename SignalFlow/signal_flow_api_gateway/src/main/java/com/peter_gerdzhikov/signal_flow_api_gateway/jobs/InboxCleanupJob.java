package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InboxCleanupService;

import lombok.RequiredArgsConstructor;

/**
 * Daily job deleting {@code topic_news_inbox} rows past the redelivery window. Every instance runs it;
 * the deletes come out the same either way.
 */
@Component
@RequiredArgsConstructor
public class InboxCleanupJob {

    private final InboxCleanupService inboxCleanupService;

    @Scheduled(
            cron = "${app.notification-inbox.cleanup-cron}",
            zone = "${app.notification-inbox.cleanup-zone}"
    )
    public void cleanUpInbox() {
        inboxCleanupService.deleteProcessedNewsOlderThanRetention();
    }
}
