package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionReconciliationService;

import lombok.RequiredArgsConstructor;

/**
 * Hard-deletes subscriptions whose topic doesn't exist - made-up ids and topics deleted since. Every
 * instance runs it; the deletes come out the same either way.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionReconciliationJob {

    private final SubscriptionReconciliationService subscriptionReconciliationService;

    @Scheduled(
            cron = "${app.subscriptions.reconciliation.cron}",
            zone = "${app.subscriptions.reconciliation.zone}"
    )
    public void reconcileSubscriptions() {
        subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();
    }
}
