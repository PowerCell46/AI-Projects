package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionReconciliationService;

@ExtendWith(MockitoExtension.class)
class SubscriptionReconciliationJobTest {

    @Mock
    private SubscriptionReconciliationService subscriptionReconciliationService;

    @InjectMocks
    private SubscriptionReconciliationJob subscriptionReconciliationJob;

    @Test
    void should_run_the_reconciliation_when_triggered() {
        subscriptionReconciliationJob.reconcileSubscriptions();

        verify(subscriptionReconciliationService).deleteSubscriptionsToMissingTopics();
    }
}
