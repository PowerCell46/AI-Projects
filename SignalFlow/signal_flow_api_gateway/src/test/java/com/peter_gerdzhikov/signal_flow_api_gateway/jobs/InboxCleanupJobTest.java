package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InboxCleanupService;

@ExtendWith(MockitoExtension.class)
class InboxCleanupJobTest {

    @Mock
    private InboxCleanupService inboxCleanupService;

    @InjectMocks
    private InboxCleanupJob inboxCleanupJob;

    @Test
    void should_run_the_cleanup_when_triggered() {
        inboxCleanupJob.cleanUpInbox();

        verify(inboxCleanupService).deleteProcessedNewsOlderThanRetention();
    }
}
