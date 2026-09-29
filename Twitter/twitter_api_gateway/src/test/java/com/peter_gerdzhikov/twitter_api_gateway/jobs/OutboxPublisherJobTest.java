package com.peter_gerdzhikov.twitter_api_gateway.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.OutboxPublisherService;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @InjectMocks
    private OutboxPublisherJob job;

    @Test
    void should_delegate_each_run_to_the_publisher_service() {
        job.publishPending();

        verify(outboxPublisherService).publishPending();
    }
}
