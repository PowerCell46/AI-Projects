package com.peter_gerdzhikov.twitter_api_gateway.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.UnconfirmedUserCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TODO: Add a javadoc explaining what this class does
@Slf4j
@Component
@RequiredArgsConstructor
public class UnconfirmedUserCleanupJob {

    private final UnconfirmedUserCleanupService cleanupService;

    @Scheduled(
            cron = "${app.users.unconfirmed-cleanup.cron}",
            zone = "${app.users.unconfirmed-cleanup.zone}"
    )
    public void deleteExpiredUnconfirmedUsers() {
        int deleted = cleanupService.deleteExpiredUnconfirmedUsers();

        log.info("Deleted {} unconfirmed users past the retention window.", deleted);
    }
}
