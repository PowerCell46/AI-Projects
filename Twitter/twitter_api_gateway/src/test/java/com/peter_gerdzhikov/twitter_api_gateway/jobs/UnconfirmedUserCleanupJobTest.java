package com.peter_gerdzhikov.twitter_api_gateway.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.UnconfirmedUserCleanupService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockService;

@ExtendWith(MockitoExtension.class)
class UnconfirmedUserCleanupJobTest {

    @Mock
    private AdvisoryLockService advisoryLockService;

    @Mock
    private UnconfirmedUserCleanupService cleanupService;

    @InjectMocks
    private UnconfirmedUserCleanupJob job;

    @Test
    void should_delegate_each_run_to_the_cleanup_service_when_the_lock_is_free() {
        when(advisoryLockService.runIfUnlocked(eq(AdvisoryLockKey.UNCONFIRMED_USER_CLEANUP), any()))
                .thenAnswer(invocation -> Optional.of(invocation.<Supplier<Integer>>getArgument(1).get()));

        job.deleteExpiredUnconfirmedUsers();

        verify(cleanupService).deleteExpiredUnconfirmedUsers();
    }

    @Test
    void should_not_touch_the_cleanup_service_when_another_instance_holds_the_lock() {
        when(advisoryLockService.runIfUnlocked(eq(AdvisoryLockKey.UNCONFIRMED_USER_CLEANUP), any()))
                .thenReturn(Optional.empty());

        job.deleteExpiredUnconfirmedUsers();

        verifyNoInteractions(cleanupService);
    }
}
