package com.peter_gerdzhikov.twitter_api_gateway.jobs;

import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.UnconfirmedUserCleanupService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job that hard-deletes accounts which never confirmed their email within the retention window, so
 * abandoned sign-ups don't hold a username or email forever. The schedule comes from
 * {@code app.users.unconfirmed-cleanup.cron} and {@code app.users.unconfirmed-cleanup.zone}. Every instance fires it,
 * and an advisory lock lets only one of them run it at a time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnconfirmedUserCleanupJob {

    private final AdvisoryLockService advisoryLockService;

    private final UnconfirmedUserCleanupService cleanupService;

    @Scheduled(
            cron = "${app.users.unconfirmed-cleanup.cron}",
            zone = "${app.users.unconfirmed-cleanup.zone}"
    )
    public void deleteExpiredUnconfirmedUsers() {
        Optional<Integer> deleted = advisoryLockService.runIfUnlocked(
                AdvisoryLockKey.UNCONFIRMED_USER_CLEANUP,
                cleanupService::deleteExpiredUnconfirmedUsers
        );

        if (deleted.isEmpty()) {
            log.info("Skipped the unconfirmed user cleanup: another instance is running it.");
            return;
        }

        log.info("Deleted {} unconfirmed users past the retention window.", deleted.get());
    }
}
