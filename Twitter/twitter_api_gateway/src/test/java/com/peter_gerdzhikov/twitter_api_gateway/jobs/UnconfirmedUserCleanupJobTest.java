package com.peter_gerdzhikov.twitter_api_gateway.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.UnconfirmedUserCleanupService;

@ExtendWith(MockitoExtension.class)
class UnconfirmedUserCleanupJobTest {

    @Mock
    private UnconfirmedUserCleanupService cleanupService;

    @InjectMocks
    private UnconfirmedUserCleanupJob job;

    @Test
    void should_delegate_each_run_to_the_cleanup_service() {
        job.deleteExpiredUnconfirmedUsers();

        verify(cleanupService).deleteExpiredUnconfirmedUsers();
    }
}
