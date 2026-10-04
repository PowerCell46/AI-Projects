package com.peter_gerdzhikov.twitter_timeline_service.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.feed.FeedRetentionService;

@ExtendWith(MockitoExtension.class)
class FeedRetentionJobTest {

    @Mock
    private FeedRetentionService feedRetentionService;

    @InjectMocks
    private FeedRetentionJob feedRetentionJob;

    @Test
    void should_delegate_to_the_retention_service_when_it_runs() {
        feedRetentionJob.deleteExpiredFeedEntries();

        verify(feedRetentionService).deleteExpiredEntries();
    }
}
