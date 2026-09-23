package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.InterestTopicLookupFailedException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;

@ExtendWith(MockitoExtension.class)
class SubscriptionReconciliationServiceImplTest {

    private static final int BATCH_SIZE = 2;

    private static final UUID BEFORE_EVERY_TOPIC_ID = new UUID(0L, 0L);

    private static final UUID TOPIC_A = UUID.randomUUID();

    private static final UUID TOPIC_B = UUID.randomUUID();

    private static final UUID TOPIC_C = UUID.randomUUID();

    private static final UUID TOPIC_D = UUID.randomUUID();

    private static final UUID TOPIC_E = UUID.randomUUID();

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InterestTopicLookupService interestTopicLookupService;

    private SubscriptionReconciliationServiceImpl subscriptionReconciliationService;

    @BeforeEach
    void setUp() {
        subscriptionReconciliationService = new SubscriptionReconciliationServiceImpl(
                BATCH_SIZE, subscriptionRepository, interestTopicLookupService);
    }

    @Nested
    class DeleteSubscriptionsToMissingTopics {

        @Test
        void should_delete_subscriptions_to_missing_topics_and_keep_existing_ones() {
            givenBatches(List.of(TOPIC_A, TOPIC_B), List.of(TOPIC_C));
            when(interestTopicLookupService.findExistingIds(List.of(TOPIC_A, TOPIC_B))).thenReturn(Set.of(TOPIC_A));
            when(interestTopicLookupService.findExistingIds(List.of(TOPIC_C))).thenReturn(Set.of());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository).deleteAllByInterestTopicIdIn(List.of(TOPIC_B));
            verify(subscriptionRepository).deleteAllByInterestTopicIdIn(List.of(TOPIC_C));
        }

        @Test
        void should_delete_nothing_when_every_topic_exists() {
            givenBatches(List.of(TOPIC_A));
            when(interestTopicLookupService.findExistingIds(List.of(TOPIC_A))).thenReturn(Set.of(TOPIC_A));

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository, never()).deleteAllByInterestTopicIdIn(anyCollection());
        }

        @Test
        void should_make_no_lookup_when_there_are_no_subscriptions() {
            when(subscriptionRepository.findDistinctInterestTopicIdsAfter(BEFORE_EVERY_TOPIC_ID, BATCH_SIZE))
                    .thenReturn(List.of());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verifyNoInteractions(interestTopicLookupService);
            verify(subscriptionRepository, never()).deleteAllByInterestTopicIdIn(anyCollection());
        }

        @Test
        void should_split_the_lookups_at_the_configured_batch_size() {
            givenBatches(List.of(TOPIC_A, TOPIC_B), List.of(TOPIC_C, TOPIC_D), List.of(TOPIC_E));
            when(interestTopicLookupService.findExistingIds(anyCollection())).thenReturn(Set.of());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            ArgumentCaptor<List<UUID>> lookups = ArgumentCaptor.captor();
            verify(interestTopicLookupService, times(3)).findExistingIds(lookups.capture());
            assertThat(lookups.getAllValues()).containsExactly(
                    List.of(TOPIC_A, TOPIC_B), List.of(TOPIC_C, TOPIC_D), List.of(TOPIC_E));
        }

        @Test
        void should_page_on_from_the_last_id_of_the_previous_batch() {
            givenBatches(List.of(TOPIC_A, TOPIC_B), List.of());
            when(interestTopicLookupService.findExistingIds(anyCollection())).thenReturn(Set.of());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository).findDistinctInterestTopicIdsAfter(TOPIC_B, BATCH_SIZE);
        }

        @Test
        void should_not_ask_for_another_page_after_a_short_batch() {
            givenBatches(List.of(TOPIC_A));
            when(interestTopicLookupService.findExistingIds(anyCollection())).thenReturn(Set.of());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository, never()).findDistinctInterestTopicIdsAfter(eq(TOPIC_A), anyInt());
        }

        @Test
        void should_stop_the_run_keeping_earlier_deletes_when_a_lookup_fails() {
            givenBatches(List.of(TOPIC_A, TOPIC_B), List.of(TOPIC_C, TOPIC_D));
            when(interestTopicLookupService.findExistingIds(List.of(TOPIC_A, TOPIC_B))).thenReturn(Set.of());
            when(interestTopicLookupService.findExistingIds(List.of(TOPIC_C, TOPIC_D)))
                    .thenThrow(new InterestTopicLookupFailedException());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository).deleteAllByInterestTopicIdIn(List.of(TOPIC_A, TOPIC_B));
            verify(subscriptionRepository, never()).deleteAllByInterestTopicIdIn(List.of(TOPIC_C, TOPIC_D));
            verify(subscriptionRepository, never()).findDistinctInterestTopicIdsAfter(eq(TOPIC_D), anyInt());
        }

        @Test
        void should_delete_nothing_when_the_first_lookup_fails() {
            givenBatches(List.of(TOPIC_A));
            when(interestTopicLookupService.findExistingIds(any())).thenThrow(new InterestTopicLookupFailedException());

            subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

            verify(subscriptionRepository, never()).deleteAllByInterestTopicIdIn(anyCollection());
        }
    }

    /**
     * Stubs consecutive keyset pages: the first starts before every id, each next one after the previous
     * page's last id.
     */
    @SafeVarargs
    private void givenBatches(List<UUID>... batches) {
        UUID after = BEFORE_EVERY_TOPIC_ID;
        for (List<UUID> batch : batches) {
            when(subscriptionRepository.findDistinctInterestTopicIdsAfter(after, BATCH_SIZE)).thenReturn(batch);
            if (!batch.isEmpty()) {
                after = batch.getLast();
            }
        }
    }
}
