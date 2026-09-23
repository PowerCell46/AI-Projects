package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.interesttopics.InterestTopicLookupFailedException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionReconciliationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubscriptionReconciliationServiceImpl implements SubscriptionReconciliationService {

    /** Sorts before every other {@code uuid} in Postgres, so the first keyset page starts at the very beginning. */
    private static final UUID BEFORE_EVERY_TOPIC_ID = new UUID(0L, 0L);

    private final int batchSize;

    private final SubscriptionRepository subscriptionRepository;

    private final InterestTopicLookupService interestTopicLookupService;

    public SubscriptionReconciliationServiceImpl(
            @Value("${app.subscriptions.reconciliation.batch-size}") int batchSize,
            SubscriptionRepository subscriptionRepository,
            InterestTopicLookupService interestTopicLookupService
    ) {
        this.batchSize = batchSize;
        this.subscriptionRepository = subscriptionRepository;
        this.interestTopicLookupService = interestTopicLookupService;
    }

    @Override
    public void deleteSubscriptionsToMissingTopics() {
        UUID after = BEFORE_EVERY_TOPIC_ID;
        long checkedTopicIds = 0;
        long deletedSubscriptions = 0;

        List<UUID> batch = nextBatch(after);
        while (!batch.isEmpty()) {
            Set<UUID> existingIds;
            try {
                existingIds = interestTopicLookupService.findExistingIds(batch);

            } catch (InterestTopicLookupFailedException e) {
                log.warn("Subscription reconciliation stopped after checking {} interest topic ids and deleting {} subscriptions.",
                        checkedTopicIds, deletedSubscriptions, e);
                return;
            }

            deletedSubscriptions += deleteSubscriptionsToTopicsNotIn(existingIds, batch);
            checkedTopicIds += batch.size();
            after = batch.getLast();
            batch = batch.size() < batchSize ? List.of() : nextBatch(after);
        }

        log.info("Subscription reconciliation checked {} interest topic ids and deleted {} subscriptions.",
                checkedTopicIds, deletedSubscriptions);
    }

    private List<UUID> nextBatch(UUID after) {
        return subscriptionRepository.findDistinctInterestTopicIdsAfter(after, batchSize);
    }

    private int deleteSubscriptionsToTopicsNotIn(Set<UUID> existingIds, List<UUID> batch) {
        List<UUID> missingIds = batch
                .stream()
                .filter(topicId -> !existingIds.contains(topicId))
                .toList();

        return missingIds.isEmpty() ? 0 : subscriptionRepository.deleteAllByInterestTopicIdIn(missingIds);
    }
}
