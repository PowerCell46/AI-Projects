package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.DuplicateSubscriptionException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.SubscriptionLimitExceededException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions.SubscriptionNotFoundException;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    private static final int MAX_SUBSCRIPTIONS_PER_USER = 3;

    private static final UUID USER_ID = UUID.randomUUID();

    private static final UUID INTEREST_TOPIC_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService =
                new SubscriptionServiceImpl(MAX_SUBSCRIPTIONS_PER_USER, userRepository, subscriptionRepository);
    }

    @Nested
    class FindAllForUser {

        @Test
        void should_return_the_callers_subscriptions_newest_first() {
            Subscription newest = new Subscription();
            Subscription oldest = new Subscription();
            when(subscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(USER_ID))
                    .thenReturn(List.of(newest, oldest));

            assertThat(subscriptionService.findAllForUser(USER_ID)).containsExactly(newest, oldest);
        }

        @Test
        void should_return_an_empty_list_when_the_caller_has_no_subscriptions() {
            when(subscriptionRepository.findAllByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

            assertThat(subscriptionService.findAllForUser(USER_ID)).isEmpty();
        }
    }

    @Nested
    class Subscribe {

        @Test
        void should_save_and_return_the_subscription() {
            User user = new User();
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn(0L);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
            when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Subscription saved = subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID);

            assertThat(saved.getUser()).isEqualTo(user);
            assertThat(saved.getInterestTopicId()).isEqualTo(INTEREST_TOPIC_ID);
        }

        @Test
        void should_reference_the_user_without_loading_it() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn(0L);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
            when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID);

            verify(userRepository).getReferenceById(USER_ID);
            verify(userRepository, never()).findById(any());
        }

        @Test
        void should_lock_the_user_before_reading_the_subscription_count() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn(0L);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
            when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID);

            InOrder inOrder = inOrder(userRepository, subscriptionRepository);
            inOrder.verify(userRepository).lockById(USER_ID);
            inOrder.verify(subscriptionRepository).countByUser_Id(USER_ID);
            inOrder.verify(subscriptionRepository).saveAndFlush(any(Subscription.class));
        }

        @Test
        void should_throw_when_the_user_is_already_subscribed() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isInstanceOf(DuplicateSubscriptionException.class);

            verify(subscriptionRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_throw_when_the_subscription_limit_is_reached() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn((long) MAX_SUBSCRIPTIONS_PER_USER);

            assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isInstanceOf(SubscriptionLimitExceededException.class);

            verify(subscriptionRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_report_the_duplicate_when_the_user_is_both_subscribed_and_at_the_limit() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isInstanceOf(DuplicateSubscriptionException.class);

            verify(subscriptionRepository, never()).countByUser_Id(any());
        }

        @Test
        void should_translate_a_unique_constraint_violation_into_a_duplicate_subscription() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn(0L);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
            when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                    .thenThrow(dataIntegrityViolation("23505"));

            assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isInstanceOf(DuplicateSubscriptionException.class);
        }

        @Test
        void should_rethrow_a_foreign_key_violation_without_relabelling_it() {
            when(subscriptionRepository.existsByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(false);
            when(subscriptionRepository.countByUser_Id(USER_ID)).thenReturn(0L);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
            DataIntegrityViolationException fkViolation = dataIntegrityViolation("23503");
            when(subscriptionRepository.saveAndFlush(any(Subscription.class))).thenThrow(fkViolation);

            assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isSameAs(fkViolation);
        }

        private DataIntegrityViolationException dataIntegrityViolation(String sqlState) {
            return new DataIntegrityViolationException("constraint violated", new SQLException("failure", sqlState));
        }
    }

    @Nested
    class Unsubscribe {

        @Test
        void should_delete_the_subscription() {
            Subscription subscription = new Subscription();
            when(subscriptionRepository.findByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(Optional.of(subscription));

            subscriptionService.unsubscribe(USER_ID, INTEREST_TOPIC_ID);

            verify(subscriptionRepository).delete(subscription);
        }

        @Test
        void should_throw_when_there_is_no_subscription_to_remove() {
            when(subscriptionRepository.findByUser_IdAndInterestTopicId(USER_ID, INTEREST_TOPIC_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.unsubscribe(USER_ID, INTEREST_TOPIC_ID))
                    .isInstanceOf(SubscriptionNotFoundException.class);

            verify(subscriptionRepository, never()).delete(any());
        }
    }
}
